package org.folio.domain.event;

import java.util.Date;
import java.util.Set;

import org.folio.domain.Event;
import org.folio.rest.jaxrs.model.Metadata;

import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import tools.jackson.databind.ObjectMapper;

@UtilityClass
@Log4j2
public class EventMapper {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static <E extends Event> E toEvent(KafkaConsumerRecord<String, String> kafkaRecord,
    Class<E> eventType) {

    E event = OBJECT_MAPPER.readValue(kafkaRecord.value(), eventType);
    if (event.getMetadata() == null) {
      event.setMetadata(new Metadata().withCreatedDate(new Date(kafkaRecord.timestamp())));
    }
    validateEvent(event);

    return event;
  }

  private static void validateEvent(Event event) {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Set<ConstraintViolation<Event>> violations = factory.getValidator().validate(event);
      if (!violations.isEmpty()) {
        throw new ConstraintViolationException(violations);
      }
    }
  }
}
