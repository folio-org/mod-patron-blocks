package org.folio.rest.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.IntStream;

import org.folio.repository.UserSummaryRepository;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.UserSummary;

public class EventHandlerTestBase extends TestBase {

  protected UserSummaryRepository userSummaryRepository;

  protected void initUserSummaryRepository() {
    userSummaryRepository = new UserSummaryRepository(postgresClient);
  }

  protected void checkUserSummary(String summaryId, UserSummary userSummaryToCompare) {
    UserSummary userSummary = waitFor(userSummaryRepository.get(summaryId)).orElseThrow(() ->
      new AssertionError("User summary was not found: " + summaryId));

    assertEquals(userSummaryToCompare.getUserId(), userSummary.getUserId());
    assertEquals(userSummaryToCompare.getOpenLoans().size(),
      userSummary.getOpenLoans().size());
    assertEquals(userSummaryToCompare.getOpenFeesFines().size(),
      userSummary.getOpenFeesFines().size());

    IntStream.range(0, userSummary.getOpenLoans().size())
      .forEach(i -> {
        OpenLoan openLoan = userSummary.getOpenLoans().get(i);
        OpenLoan openLoanToCompare = userSummaryToCompare.getOpenLoans().get(i);
        assertEquals(openLoanToCompare.getLoanId(), openLoan.getLoanId());
        assertEquals(openLoanToCompare.getDueDate(), openLoan.getDueDate());
        assertEquals(openLoanToCompare.getRecall(), openLoan.getRecall());
        assertEquals(openLoanToCompare.getItemLost(), openLoan.getItemLost());
      });
  }

}
