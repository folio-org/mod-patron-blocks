package org.folio.repository;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

import org.folio.cql2pgjson.exception.FieldException;
import org.junit.Test;

public class BaseRepositoryTest {

  @Test
  public void handlesCQL2PgJSONException() {
    Exception thrown = assertThrows(RuntimeException.class,
        () -> new BaseRepository<Long>(null, "'", null));
    assertThat(thrown.getCause(), instanceOf(FieldException.class));
  }

}
