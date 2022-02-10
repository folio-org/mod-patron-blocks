package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.util.stream.Collectors.toMap;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.domain.ActionBlocks;
import org.folio.repository.PatronBlockConditionsRepository;
import org.folio.repository.PatronBlockLimitsRepository;
import org.folio.rest.client.UsersClient;
import org.folio.rest.jaxrs.model.AutomatedPatronBlock;
import org.folio.rest.jaxrs.model.AutomatedPatronBlocks;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.PatronBlockCondition;
import org.folio.rest.jaxrs.model.PatronBlockLimit;
import org.folio.rest.jaxrs.model.UserSummary;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.util.AsyncProcessingContext;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

public class PatronBlocksService {
  private static final Logger log = LogManager.getLogger(PatronBlocksService.class);

  private static final String DEFAULT_ERROR_MESSAGE = "Failed to calculate automated patron blocks";
  private static final String OVERDUE_MINUTES_CALCULATION_ERROR_TEMPLATE =
    "Failed to calculate overdue minutes: {}";

  private static final BinaryOperator<Integer> OVERDUE_MINUTES_MERGE_FUNCTION = (oldValue, newValue) -> {
    log.warn("Two open loans with the same loanId found! Newest overdue minutes amount" +
      " saved. Old value: {}, new value: {}", oldValue, newValue);
    return newValue;
  };
  private final UserSummaryService userSummaryService;
  private final PatronBlockConditionsRepository conditionsRepository;
  private final PatronBlockLimitsRepository limitsRepository;
  private final UsersClient usersClient;

  public PatronBlocksService(Map<String, String> okapiHeaders, Vertx vertx) {
    String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(TENANT));
    PostgresClient postgresClient = PostgresClient.getInstance(vertx, tenantId);
    userSummaryService = new UserSummaryService(postgresClient);
    conditionsRepository = new PatronBlockConditionsRepository(postgresClient);
    limitsRepository = new PatronBlockLimitsRepository(postgresClient);
    usersClient = new UsersClient(vertx, okapiHeaders);
  }

  public Future<AutomatedPatronBlocks> getBlocksForUser(String userId) {
    return userSummaryService.getByUserId(userId)
      .map(userSummary -> new BlocksCalculationContext().withUserSummary(userSummary))
      .compose(this::getBlocksForSummary)
      .otherwise(new AutomatedPatronBlocks());
  }

  private Future<AutomatedPatronBlocks> getBlocksForSummary(BlocksCalculationContext ctx) {
    return succeededFuture(ctx)
      .compose(this::addUserGroupIdToContext)
      .compose(this::addPatronBlockLimitsToContext)
      .compose(this::addAllPatronBlockConditionsToContext)
      .map(this::addOverdueMinutesToContext)
      .map(this::calculateBlocks);
  }

  private AutomatedPatronBlocks calculateBlocks(BlocksCalculationContext ctx) {
    final AutomatedPatronBlocks blocks = new AutomatedPatronBlocks();

    if (ctx.shouldCalculationBeSkipped()) {
      return blocks;
    }

    blocks.getAutomatedPatronBlocks().addAll(ctx.patronBlockLimits.stream()
      .map(ctx::withCurrentPatronBlockLimit)
      .map(this::addCurrentConditionToContext)
      .map(this::addActionBlocksByLimitAndConditionToContext)
      .filter(context -> context.currentActionBlocks.isNotEmpty())
      .map(this::createBlockForLimit)
      .filter(Objects::nonNull)
      .collect(Collectors.toList()));

    return blocks;
  }

  private Future<BlocksCalculationContext> addUserGroupIdToContext(BlocksCalculationContext ctx) {
    if (ctx.userSummary == null || ctx.userSummary.getUserId() == null) {
      ctx.logFailedValidationError("addUserGroupIdToContext");
      return failedFuture(DEFAULT_ERROR_MESSAGE);
    }

    return usersClient.findPatronGroupIdForUser(ctx.userSummary.getUserId())
      .map(ctx::withUserGroupId);
  }

  private Future<BlocksCalculationContext> addPatronBlockLimitsToContext(
    BlocksCalculationContext ctx) {

    if (ctx.userGroupId == null) {
      ctx.logFailedValidationError("addPatronBlockLimitsToContext");
      return failedFuture(DEFAULT_ERROR_MESSAGE);
    }

    return limitsRepository.findLimitsForPatronGroup(ctx.userGroupId)
      .map(ctx::withPatronBlockLimits);
  }

  private Future<BlocksCalculationContext> addAllPatronBlockConditionsToContext(
    BlocksCalculationContext ctx) {

    if (ctx.shouldCalculationBeSkipped()) {
      return succeededFuture(ctx);
    }

    return conditionsRepository.getAllWithDefaultLimit().map(ctx::withPatronBlockConditions);
  }

  private BlocksCalculationContext addOverdueMinutesToContext(BlocksCalculationContext ctx) {
    if (ctx.shouldCalculationBeSkipped()) {
      return ctx;
    }

    return ctx.withOverdueMinutes(
      ctx.userSummary.getOpenLoans()
        .stream()
        .filter(PatronBlocksService::validateLoan)
        .collect(toMap(OpenLoan::getLoanId, OverduePeriodCalculator::calculateOverdueMinutes,
          OVERDUE_MINUTES_MERGE_FUNCTION)));
  }

  private static boolean validateLoan(OpenLoan openLoan) {
    if (openLoan == null) {
      log.error(OVERDUE_MINUTES_CALCULATION_ERROR_TEMPLATE, "openLoan is null");
      return false;
    }

    if (openLoan.getDueDate() == null) {
      log.error(OVERDUE_MINUTES_CALCULATION_ERROR_TEMPLATE, "due date is null");
      return false;
    }

    return true;
  }

  private BlocksCalculationContext addCurrentConditionToContext(
    BlocksCalculationContext ctx) {

    if (ctx.currentPatronBlockLimit == null ||
      ctx.currentPatronBlockLimit.getConditionId() == null) {

      ctx.logFailedValidationError("addCurrentConditionToContext");
      return ctx;
    }

    String conditionId = ctx.currentPatronBlockLimit.getConditionId();

    PatronBlockCondition patronBlockCondition = ctx.patronBlockConditions.stream()
      .filter(condition -> condition.getId().equals(conditionId))
      .findFirst()
      .orElse(null);

    if (patronBlockCondition == null) {
      ctx.logFailedValidationError("addCurrentConditionToContext",
        format("Cannot find condition by ID %s", conditionId));
      return ctx;
    }

    return ctx.withCurrentPatronBlockCondition(patronBlockCondition);
  }

  private BlocksCalculationContext addActionBlocksByLimitAndConditionToContext(
    BlocksCalculationContext ctx) {

    if (ctx.userSummary == null || ctx.currentPatronBlockLimit == null ||
      ctx.currentPatronBlockCondition == null || ctx.overdueMinutes == null) {

      ctx.logFailedValidationError("addActionBlocksByLimitAndConditionToContext");
      return ctx;
    }

    PatronBlockCondition patronBlockCondition = ctx.currentPatronBlockCondition;

    ActionBlocks actionBlocksByLimit = ActionBlocks.byLimit(ctx.userSummary,
      ctx.currentPatronBlockLimit, ctx.overdueMinutes);

    ActionBlocks actionBlocksByCondition = new ActionBlocks(
      Boolean.TRUE.equals(patronBlockCondition.getBlockBorrowing()),
      Boolean.TRUE.equals(patronBlockCondition.getBlockRenewals()),
      Boolean.TRUE.equals(patronBlockCondition.getBlockRequests()));

    return ctx.withCurrentActionBlocks(
      ActionBlocks.and(actionBlocksByLimit, actionBlocksByCondition));
  }

  private AutomatedPatronBlock createBlockForLimit(BlocksCalculationContext ctx) {
    if (ctx.currentPatronBlockCondition == null || ctx.currentActionBlocks == null) {
      ctx.logFailedValidationError("createBlockForLimit");
      return null;
    }

    return new AutomatedPatronBlock()
      .withPatronBlockConditionId(ctx.currentPatronBlockCondition.getId())
      .withBlockBorrowing(ctx.currentActionBlocks.getBlockBorrowing())
      .withBlockRenewals(ctx.currentActionBlocks.getBlockRenewals())
      .withBlockRequests(ctx.currentActionBlocks.getBlockRequests())
      .withMessage(ctx.currentPatronBlockCondition.getMessage());
  }

  @With
  @AllArgsConstructor
  @NoArgsConstructor(force = true)
  private static class BlocksCalculationContext extends AsyncProcessingContext {
    final UserSummary userSummary;
    final String userGroupId;
    final List<PatronBlockLimit> patronBlockLimits;
    final List<PatronBlockCondition> patronBlockConditions;
    final Map<String, Integer> overdueMinutes;
    final PatronBlockLimit currentPatronBlockLimit;
    final PatronBlockCondition currentPatronBlockCondition;
    final ActionBlocks currentActionBlocks;

    @Override
    protected String getName() {
      return "blocks-calculation-context";
    }

    protected boolean shouldCalculationBeSkipped() {
      return this.patronBlockLimits == null || this.patronBlockLimits.isEmpty();
    }
  }

}
