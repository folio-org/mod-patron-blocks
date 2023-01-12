package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static org.folio.domain.EventType.ITEM_CHECKED_OUT;
import static org.folio.domain.EventType.getByEvent;
import static org.folio.domain.EventType.getNameByEvent;
import static org.folio.util.LogUtil.asJson;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.domain.Event;
import org.folio.domain.EventType;
import org.folio.domain.FeeFineType;
import org.folio.exception.EntityNotFoundInDbException;
import org.folio.repository.UserSummaryRepository;
import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.jaxrs.model.ItemAgedToLostEvent;
import org.folio.rest.jaxrs.model.ItemCheckedInEvent;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.ItemClaimedReturnedEvent;
import org.folio.rest.jaxrs.model.ItemDeclaredLostEvent;
import org.folio.rest.jaxrs.model.LoanClosedEvent;
import org.folio.rest.jaxrs.model.LoanDueDateChangedEvent;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.OpenFeeFine;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.UserSummary;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.util.AsyncProcessingContext;

import io.vertx.core.Future;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;

public class UserSummaryService {
  private static final Logger log = LogManager.getLogger(UserSummaryService.class);

  private static final String LOG_TEMPLATE_UPDATE_USER_SUMMARY =
    "updateUserSummary:: parameters userSummary: {}, event: {}";
  private static final String FAILED_TO_REBUILD_USER_SUMMARY_ERROR_MESSAGE =
    "Failed to rebuild user summary";
  private static final int MAX_NUMBER_OF_RETRIES_ON_VERSION_CONFLICT = 10;
  private static final List<String> LOST_ITEM_FEE_TYPE_IDS = Arrays.asList(
    FeeFineType.LOST_ITEM_FEE.getId(),
    FeeFineType.LOST_ITEM_PROCESSING_FEE.getId()
  );

  private final UserSummaryRepository userSummaryRepository;
  private final EventService eventService;

  public UserSummaryService(PostgresClient postgresClient) {
    userSummaryRepository = new UserSummaryRepository(postgresClient);
    eventService = new EventService(postgresClient);
  }

  public Future<UserSummary> getByUserId(String userId) {
    log.debug("getByUserId:: parameters userId: {}", userId);
    return userSummaryRepository.getByUserId(userId)
      .map(optionalUserSummary -> optionalUserSummary.orElseThrow(() ->
        new EntityNotFoundInDbException(format("User summary for user ID %s not found", userId))))
      .onSuccess(result -> log.info("getByUserId:: result: {}", () -> asJson(result)));
  }

  public Future<String> updateUserSummaryWithEvent(UserSummary userSummary, Event event) {
    log.debug("updateUserSummaryWithEvent:: parameters userSummary: {}, event: {}",
      () -> asJson(userSummary), () -> asJson(event));
    return recursivelyUpdateUserSummaryWithEvent(new UpdateRetryContext(userSummary), event)
      .onSuccess(result -> log.info("updateUserSummaryWithEvent:: result: {}", result));
  }

  private Future<String> recursivelyUpdateUserSummaryWithEvent(UpdateRetryContext ctx,
      Event event) {

    log.debug("recursivelyUpdateUserSummaryWithEvent:: parameters ctx: {}, event: {}",
      () -> asJson(ctx), () -> asJson(event));
    return updateAndStoreUserSummary(ctx.userSummary, event)
      .recover(throwable -> {
        log.warn("recursivelyUpdateUserSummaryWithEvent:: Failed to update user summary", throwable);
        if (! PgExceptionUtil.isVersionConflict(throwable)) {
          return Future.failedFuture(throwable);
        }
        if (! ctx.shouldRetryUpdate()) {
          log.warn("recursivelyUpdateUserSummaryWithEvent:: Failed to update user summary due " +
              "to version conflict. User ID: {}. Failed attempts: {}", ctx.userSummary.getUserId(),
              MAX_NUMBER_OF_RETRIES_ON_VERSION_CONFLICT, throwable);
          return Future.failedFuture(throwable);
        }
        log.warn("recursivelyUpdateUserSummaryWithEvent:: Version conflict when trying to update " +
            "user summary. User ID: {}. Attempt # {} of {}", ctx.userSummary.getUserId(),
            ctx.attemptCounter.get(), MAX_NUMBER_OF_RETRIES_ON_VERSION_CONFLICT, throwable);
        return userSummaryRepository.findByUserIdOrBuildNew(ctx.userSummary.getUserId())
            .compose(latestVersionUserSummary -> {
              ctx.attemptCounter.incrementAndGet();
              ctx.setUserSummary(latestVersionUserSummary);
              return recursivelyUpdateUserSummaryWithEvent(ctx, event);
            })
          .onSuccess(result -> log.info("recursivelyUpdateUserSummaryWithEvent:: result: {}", result));
      });
  }

  private Future<String> updateAndStoreUserSummary(UserSummary userSummary, Event event) {
    log.debug("updateAndStoreUserSummary:: parameters userSummary: {}, event: {}",
      () -> asJson(userSummary), () -> asJson(event));
    RebuildContext rebuildContext = new RebuildContext().withUserSummary(userSummary);
    handleEvent(rebuildContext, event);

    if (isNotEmpty(rebuildContext.userSummary)) {
      log.info("updateAndStoreUserSummary:: user summary is not empty");
      return userSummaryRepository.upsert(rebuildContext.userSummary)
        .onSuccess(result -> log.info("updateAndStoreUserSummary:: result: {}", result));
    } else {
      log.info("updateAndStoreUserSummary:: user summary is empty");
      return userSummaryRepository.delete(
        Objects.requireNonNull(rebuildContext.userSummary).getId())
        .map(rebuildContext.userSummary.getId())
        .otherwise(rebuildContext.userSummary.getId())
        .onSuccess(result -> log.info("updateAndStoreUserSummary:: result: {}", result));
    }
  }

  public Future<String> rebuild(String userId) {
    log.debug("rebuild:: parameters userId: {}", userId);

    return userSummaryRepository.findByUserIdOrBuildNew(userId)
      .map(userSummary -> new RebuildContext().withUserSummary(userSummary))
      .compose(this::loadEventsToContext)
      .compose(this::cleanUpUserSummary)
      .compose(this::handleEventsInChronologicalOrder)
      .onSuccess(result -> log.info("rebuild:: result: {}", result));
  }

  private Future<RebuildContext> loadEventsToContext(RebuildContext ctx) {
    log.debug("loadEventsToContext:: parameters ctx: {}", () -> asJson(ctx));
    if (ctx.userSummary == null || ctx.userSummary.getUserId() == null) {
      ctx.logFailedValidationError("loadEventsToContext");
      return failedFuture(FAILED_TO_REBUILD_USER_SUMMARY_ERROR_MESSAGE);
    }

    String userId = ctx.userSummary.getUserId();

    return succeededFuture(userId)
      .compose(eventService::getItemCheckedOutEvents)
      .map(ctx.events::addAll)
      .map(userId)
      .compose(eventService::getItemCheckedInEvents)
      .map(ctx.events::addAll)
      .map(userId)
      .compose(eventService::getItemClaimedReturnedEvents)
      .map(ctx.events::addAll)
      .map(userId)
      .compose(eventService::getItemDeclaredLostEvents)
      .map(ctx.events::addAll)
      .map(userId)
      .compose(eventService::getItemAgedToLostEvents)
      .map(ctx.events::addAll)
      .map(userId)
      .compose(eventService::getLoanDueDateChangedEvents)
      .map(ctx.events::addAll)
      .map(userId)
      .compose(eventService::getFeeFineBalanceChangedEvents)
      .map(ctx.events::addAll)
      .map(ctx)
      .onSuccess(result -> log.info("loadEventsToContext:: result: {}", () -> asJson(result)));
  }

  private Future<RebuildContext> cleanUpUserSummary(RebuildContext ctx) {
    log.debug("cleanUpUserSummary:: parameters ctx: {}", () -> asJson(ctx));
    if (ctx.userSummary == null) {
      ctx.logFailedValidationError("cleanUpUserSummary");
      return failedFuture(FAILED_TO_REBUILD_USER_SUMMARY_ERROR_MESSAGE);
    }

    ctx.userSummary.setOpenLoans(new ArrayList<>());
    ctx.userSummary.setOpenFeesFines(new ArrayList<>());

    log.info("cleanUpUserSummary:: result: {}", () -> asJson(ctx));
    return succeededFuture(ctx);
  }

  private Future<String> handleEventsInChronologicalOrder(RebuildContext ctx) {
    log.debug("handleEventsInChronologicalOrder:: parameters ctx: {}", () -> asJson(ctx));
    if (ctx.userSummary == null || ctx.userSummary.getUserId() == null) {
      ctx.logFailedValidationError("loadEventsToContext");
      return failedFuture(FAILED_TO_REBUILD_USER_SUMMARY_ERROR_MESSAGE);
    }

    ctx.events.stream()
      .sorted(Comparator.comparingLong(event -> Optional.of(event)
        .map(Event::getMetadata)
        .map(Metadata::getCreatedDate)
        .map(Date::getTime)
        .orElse(0L)))
      .forEachOrdered(event -> handleEvent(ctx, event));

    if (isNotEmpty(ctx.userSummary)) {
      log.info("handleEventsInChronologicalOrder:: user summary is not empty");
      return userSummaryRepository.upsert(ctx.userSummary, ctx.userSummary.getId())
        .onSuccess(result -> log.info("handleEventsInChronologicalOrder:: result: {}", result));
    } else {
      log.info("handleEventsInChronologicalOrder:: user summary is empty");
      return userSummaryRepository.delete(ctx.userSummary.getId())
        .map(ctx.userSummary.getId())
        .otherwise(ctx.userSummary.getId())
        .onSuccess(result -> log.info("handleEventsInChronologicalOrder:: result: {}", result));
    }
  }

  private void handleEvent(RebuildContext ctx, Event event) {
    log.debug("handleEvent:: parameters ctx: {}, event: {}", () -> asJson(ctx),
      () -> asJson(event));
    if (ctx.userSummary == null || event == null || getByEvent(event) == null ||
      event.getMetadata() == null) {

      ctx.logFailedValidationError("handleEvent");
      return;
    }

    EventType eventType = getByEvent(event);

    switch (eventType) {
      case ITEM_CHECKED_OUT:
        updateUserSummary(ctx.userSummary, (ItemCheckedOutEvent) event);
        break;
      case ITEM_CHECKED_IN:
        updateUserSummary(ctx.userSummary, (ItemCheckedInEvent) event);
        break;
      case ITEM_CLAIMED_RETURNED:
        updateUserSummary(ctx.userSummary, (ItemClaimedReturnedEvent) event);
        break;
      case ITEM_DECLARED_LOST:
        updateUserSummary(ctx.userSummary, (ItemDeclaredLostEvent) event);
        break;
      case ITEM_AGED_TO_LOST:
        updateUserSummary(ctx.userSummary, (ItemAgedToLostEvent) event);
        break;
      case LOAN_DUE_DATE_CHANGED:
        updateUserSummary(ctx.userSummary, (LoanDueDateChangedEvent) event);
        break;
      case FEE_FINE_BALANCE_CHANGED:
        updateUserSummary(ctx.userSummary, (FeeFineBalanceChangedEvent) event);
        break;
      case LOAN_CLOSED:
        updateUserSummary(ctx.userSummary, (LoanClosedEvent) event);
        break;
    }
  }

  private void updateUserSummary(UserSummary userSummary, ItemCheckedOutEvent event) {
    log.debug(LOG_TEMPLATE_UPDATE_USER_SUMMARY, () -> asJson(userSummary),
      () -> asJson(event));
    List<OpenLoan> openLoans = userSummary.getOpenLoans();

    if (openLoans.stream()
      .noneMatch(loan -> StringUtils.equals(loan.getLoanId(), event.getLoanId()))) {

      log.info("updateUserSummary:: openLoans does not contain loans with loanId {}", event.getLoanId());
      openLoans.add(new OpenLoan()
        .withLoanId(event.getLoanId())
        .withDueDate(event.getDueDate())
        .withGracePeriod(event.getGracePeriod()));
    } else {
      log.info("updateUserSummary:: Event {}:{} is ignored. Open loan {} already exists",
        ITEM_CHECKED_OUT.name(), event.getId(), event.getLoanId());
    }
  }

  private void updateUserSummary(UserSummary userSummary, ItemCheckedInEvent event) {
    log.debug(LOG_TEMPLATE_UPDATE_USER_SUMMARY, () -> asJson(userSummary),
      () -> asJson(event));
    removeLoanFromUserSummary(userSummary, event, event.getLoanId());
  }

  private void removeLoanFromUserSummary(UserSummary userSummary, Event event, String loanId) {
    log.debug("removeLoanFromUserSummary:: parameters userSummary: {}, event: {}, loanId: {}",
      () -> asJson(userSummary), () -> asJson(event), () -> loanId);
    boolean loanRemoved = userSummary.getOpenLoans()
      .removeIf(loan -> StringUtils.equals(loan.getLoanId(), loanId));

    if (!loanRemoved) {
      log.info("removeLoanFromUserSummary:: loan is removed");
      logOpenLoanNotFound(event, loanId);
    }
  }

  private void updateUserSummary(UserSummary userSummary, ItemClaimedReturnedEvent event) {
    log.debug(LOG_TEMPLATE_UPDATE_USER_SUMMARY, () -> asJson(userSummary),
      () -> asJson(event));
    userSummary.getOpenLoans().stream()
      .filter(loan -> StringUtils.equals(loan.getLoanId(), event.getLoanId()))
      .findFirst()
      .ifPresentOrElse(openLoan -> {
        openLoan.setItemClaimedReturned(true);
        openLoan.setItemLost(false);
      }, () -> logOpenLoanNotFound(event, event.getLoanId()));
  }

  private void updateUserSummary(UserSummary userSummary, ItemDeclaredLostEvent event) {
    log.debug(LOG_TEMPLATE_UPDATE_USER_SUMMARY, () -> asJson(userSummary),
      () -> asJson(event));
    updateUserSummaryForLostItem(userSummary, event, event.getLoanId());
  }

  private void updateUserSummary(UserSummary userSummary, ItemAgedToLostEvent event) {
    log.debug(LOG_TEMPLATE_UPDATE_USER_SUMMARY, () -> asJson(userSummary),
      () -> asJson(event));
    updateUserSummaryForLostItem(userSummary, event, event.getLoanId());
  }

  private void updateUserSummaryForLostItem(UserSummary userSummary, Event event, String loanId) {
    log.debug("updateUserSummaryForLostItem:: parameters userSummary: {}, event: {}, loanId: {}",
      () -> asJson(userSummary), () -> asJson(event), () -> loanId);
    userSummary.getOpenLoans().stream()
      .filter(loan -> StringUtils.equals(loan.getLoanId(), loanId))
      .findAny()
      .ifPresentOrElse(openLoan -> {
        openLoan.setItemLost(true);
        openLoan.setItemClaimedReturned(false);
      }, () -> logOpenLoanNotFound(event, loanId));
  }

  private void updateUserSummary(UserSummary userSummary, LoanDueDateChangedEvent event) {
    log.debug(LOG_TEMPLATE_UPDATE_USER_SUMMARY, () -> asJson(userSummary), () -> asJson(event));
    userSummary.getOpenLoans().stream()
      .filter(loan -> StringUtils.equals(loan.getLoanId(), event.getLoanId()))
      .findFirst()
      .ifPresentOrElse(openLoan -> {
        openLoan.setDueDate(event.getDueDate());
        openLoan.setRecall(event.getDueDateChangedByRecall());
        if (Boolean.FALSE.equals(event.getDueDateChangedByRecall())){
          openLoan.setItemLost(false);
        }
      }, () -> logOpenLoanNotFound(event, event.getLoanId()));
  }

  private void logOpenLoanNotFound(Event event, String loanId){
    log.error("Event {}:{} is ignored. Open loan {} was not found for user {}",
      getNameByEvent(event), event.getId(), loanId, event.getUserId());
  }

  private void updateUserSummary(UserSummary userSummary, FeeFineBalanceChangedEvent event) {
    log.debug(LOG_TEMPLATE_UPDATE_USER_SUMMARY, () -> asJson(userSummary), () -> asJson(event));
    List<OpenFeeFine> openFeesFines = userSummary.getOpenFeesFines();

    OpenFeeFine openFeeFine = openFeesFines.stream()
      .filter(feeFine -> StringUtils.equals(feeFine.getFeeFineId(), event.getFeeFineId()))
      .findFirst()
      .orElseGet(() -> {
        OpenFeeFine newFeeFine = new OpenFeeFine()
          .withFeeFineId(event.getFeeFineId())
          .withFeeFineTypeId(event.getFeeFineTypeId())
          .withBalance(event.getBalance());
        openFeesFines.add(newFeeFine);
        return newFeeFine;
      });

    if (feeFineIsClosed(event)) {
      log.info("updateUserSummary:: fee/fine is closed");
      openFeesFines.remove(openFeeFine);
    } else {
      log.info("updateUserSummary:: fee/fine is open");
      openFeeFine.setBalance(event.getBalance());
      openFeeFine.setLoanId(event.getLoanId());
    }
  }

  private void updateUserSummary(UserSummary userSummary, LoanClosedEvent event) {
    log.debug(LOG_TEMPLATE_UPDATE_USER_SUMMARY, () -> asJson(userSummary), () -> asJson(event));
    removeLoanFromUserSummary(userSummary, event, event.getLoanId());
  }

  private boolean feeFineIsClosed(FeeFineBalanceChangedEvent event) {
    return BigDecimal.ZERO.compareTo(event.getBalance()) == 0;
  }

  private boolean isLostItemFeeId(String feeFineTypeId) {
    return LOST_ITEM_FEE_TYPE_IDS.contains(feeFineTypeId);
  }

  private boolean isEmpty(UserSummary userSummary) {
    if (userSummary != null && userSummary.getOpenLoans() != null &&
      userSummary.getOpenFeesFines() != null) {

      return userSummary.getOpenLoans().isEmpty() && userSummary.getOpenFeesFines().isEmpty();
    }

    return true;
  }

  private boolean isNotEmpty(UserSummary userSummary) {
    return !isEmpty(userSummary);
  }

  @With
  @AllArgsConstructor
  @NoArgsConstructor(force = true)
  @Getter
  private static class RebuildContext extends AsyncProcessingContext {
    final UserSummary userSummary;
    final List<Event> events = new ArrayList<>();

    @Override
    protected String getName() {
      return "user-summary-rebuild-context";
    }
  }

  @Getter
  private static class UpdateRetryContext {
    @Setter
    private UserSummary userSummary;

    private final AtomicInteger attemptCounter = new AtomicInteger(1);

    public UpdateRetryContext(UserSummary userSummary) {
      this.userSummary = userSummary;
    }

    boolean shouldRetryUpdate() {
      return attemptCounter.get() <= MAX_NUMBER_OF_RETRIES_ON_VERSION_CONFLICT;
    }
  }
}
