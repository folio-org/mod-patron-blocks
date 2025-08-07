package org.folio.service;

import static org.apache.commons.lang3.reflect.FieldUtils.writeField;
import static org.folio.rest.jaxrs.model.SynchronizationJob.Scope.FULL;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.folio.domain.SynchronizationStatus;
import org.folio.rest.jaxrs.model.SynchronizationJob;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.SneakyThrows;

public class SynchronizationJobServiceTest {
  private static final int BATCH_SIZE = 20;
  private UserSummaryService userSummaryService;
  private LoanEventsGenerationService loanEventsGenerationService;
  private FeesFinesEventsGenerationService feesFinesEventsGenerationService;
  private SynchronizationJobService service;

  @SneakyThrows
  @Before
  public void setUp() {
    userSummaryService = mock(UserSummaryService.class);
    loanEventsGenerationService = mock(LoanEventsGenerationService.class);
    feesFinesEventsGenerationService = mock(FeesFinesEventsGenerationService.class);
    service = Mockito.spy(new SynchronizationJobService(new HashMap<>(), mock(Vertx.class)));
    writeField(service, "userSummaryService", userSummaryService, true);
    writeField(service, "loanEventsGenerationService", loanEventsGenerationService, true);
    writeField(service, "feesFinesEventsGenerationService", feesFinesEventsGenerationService, true);
  }

  @Test
  public void shouldProcessUserSummariesInBatches() {
    int userCount = BATCH_SIZE + 5;
    List<String> userIds = IntStream.range(0, userCount)
      .mapToObj(i -> "user-" + i)
      .toList();
    when(loanEventsGenerationService.getUserIds()).thenReturn(new HashSet<>(userIds));
    when(feesFinesEventsGenerationService.getUserIds()).thenReturn(new HashSet<>());
    when(userSummaryService.rebuild(anyString())).thenReturn(Future.succeededFuture("ok"));

    SynchronizationJob job = new SynchronizationJob()
      .withId("job-1")
      .withScope(FULL)
      .withStatus(SynchronizationStatus.OPEN.getValue());

    // When
    service.rebuildUserSummaries(job).toCompletionStage().toCompletableFuture().join();

    // Then
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(userSummaryService, times(userCount)).rebuild(captor.capture());
    Set<String> calledUserIds = new HashSet<>(captor.getAllValues());
    assertEquals(new HashSet<>(userIds), calledUserIds);
  }
}
