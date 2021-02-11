package org.folio.util;

import java.util.List;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.folio.okapi.common.GenericCompositeFuture;

public interface CustomCompositeFuture extends CompositeFuture {
  static <T> CompositeFuture all(List<Future<T>> futures) {
    return GenericCompositeFuture.all(futures);
  }
}
