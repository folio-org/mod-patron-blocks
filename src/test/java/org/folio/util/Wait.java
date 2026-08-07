package org.folio.util;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.hamcrest.Matcher;

import io.vertx.core.Future;
import lombok.SneakyThrows;

public class Wait {

  private static final int DEFAULT_TIMEOUT_SECONDS = 30;

  private Wait() { }

  public static <T> Collection<T> waitForSize(Callable<Collection<T>> supplier, int expectedSize) {
    return waitForValue(supplier, (Predicate<Collection<T>>) c -> c.size() == expectedSize);
  }

  public static <T> T waitForValue(Callable<T> valueSupplier, T expected) {
    return waitForValue(valueSupplier, (Predicate<T>) actual -> Objects.equals(actual, expected));
  }

  public static <T> T waitForValue(Callable<T> valueSupplier, Predicate<T> valuePredicate) {
    return waitAtMost(DEFAULT_TIMEOUT_SECONDS, SECONDS)
      .until(valueSupplier, valuePredicate);
  }

  public static <T> T waitForValueMatching(Callable<T> valueSupplier, Matcher<T> valueMatcher) {
    return waitAtMost(DEFAULT_TIMEOUT_SECONDS, SECONDS)
      .until(valueSupplier, valueMatcher);
  }

  public static void waitFor(Callable<Boolean> conditionEvaluator) {
    waitAtMost(DEFAULT_TIMEOUT_SECONDS, SECONDS)
      .until(conditionEvaluator);
  }

  public static <T> T waitFor(Future<T> future) {
    return waitFor(future, DEFAULT_TIMEOUT_SECONDS);
  }

  @SneakyThrows
  public static <T> T waitFor(Future<T> future, int timeoutSeconds) {
    return future.toCompletionStage()
      .toCompletableFuture()
      .get(timeoutSeconds, SECONDS);
  }

  public static <T> T waitFor(CompletableFuture<T> future) {
    return waitFor(future, DEFAULT_TIMEOUT_SECONDS);
  }

  @SneakyThrows
  public static <T> T waitFor(CompletableFuture<T> future, int timeoutSeconds) {
    return future.get(timeoutSeconds, SECONDS);
  }
}
