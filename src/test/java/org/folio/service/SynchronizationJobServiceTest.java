package org.folio.service;

import static org.apache.commons.lang3.reflect.FieldUtils.writeField;
import static org.folio.rest.jaxrs.model.SynchronizationJob.Scope.FULL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import org.folio.domain.SynchronizationStatus;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.SynchronizationJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.SneakyThrows;

public class SynchronizationJobServiceTest extends TestBase {
  private static final int BATCH_SIZE = 20;

  @Mock
  private UserSummaryService userSummaryService;
  @Mock
  private LoanEventsGenerationService loanEventsGenerationService;
  @Mock
  private FeesFinesEventsGenerationService feesFinesEventsGenerationService;
  @Spy
  private SynchronizationJobService service = new SynchronizationJobService(new HashMap<>(), Mockito.mock(Vertx.class));
  @Captor
  private ArgumentCaptor<List<String>> userIdsCaptor;

  @SneakyThrows
  @BeforeEach
  void setUp() {
    openMocks(this);
    writeField(service, "userSummaryService", userSummaryService, true);
    writeField(service, "loanEventsGenerationService", loanEventsGenerationService, true);
    writeField(service, "feesFinesEventsGenerationService", feesFinesEventsGenerationService, true);
  }

  @Test
  void shouldProcessUserSummariesInBatches() {
    int userCount = BATCH_SIZE + 5; // 20 (batch size) + 5 (last batch)
    List<String> userIds = IntStream.range(0, userCount)
      .mapToObj(i -> "user-" + i)
      .toList();
    when(loanEventsGenerationService.getUserIds()).thenReturn(new HashSet<>(userIds));
    when(feesFinesEventsGenerationService.getUserIds()).thenReturn(new HashSet<>());
    when(userSummaryService.rebuild(Mockito.anyString())).thenReturn(Future.succeededFuture("ok"));

    SynchronizationJob job = new SynchronizationJob()
      .withId("job-1")
      .withScope(FULL)
      .withStatus(SynchronizationStatus.OPEN.getValue());

    // When
    service.rebuildUserSummaries(job).toCompletionStage().toCompletableFuture().join();

    // Then
    Mockito.verify(service, times(2)).processBatch(userIdsCaptor.capture());
    List<List<String>> batches = userIdsCaptor.getAllValues();
    assertEquals(2, batches.size());
    assertEquals(20, batches.get(0).size());
    assertEquals(5, batches.get(1).size());
  }
}
