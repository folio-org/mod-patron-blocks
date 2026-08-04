package org.folio.domain.event;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.folio.domain.Event;

import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

@UtilityClass
@Log4j2
public class DomainEventMapper {

  public static <T extends Event> DomainEvent<T> toDomainEvent(KafkaConsumerRecord<String, String> record,
    Class<T> eventPayloadType) {

    JsonObject value = new JsonObject(record.value());
    T payload = Optional.ofNullable(value.getJsonObject("data"))
      .map(json -> json.mapTo(eventPayloadType))
      .orElseThrow();

    validatePayload(payload);

    return new DomainEvent<>(
      UUID.fromString(value.getString("id")),
      requireNonNull(value.getString("tenant")),
      requireNonNull(value.getLong("timestamp")),
      payload
    );
  }

  private static void validatePayload(Event eventPayload) {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Set<ConstraintViolation<Event>> violations = factory.getValidator().validate(eventPayload);
      if (violations.isEmpty()) {
        log.debug("validatePayload:: event payload validation passed");
        return;
      }
      throw new ConstraintViolationException(violations);
    }
  }

}
