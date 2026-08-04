package org.folio.service;

import java.util.Set;

import org.folio.domain.Event;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EventValidator {

  public static void validate(Event event) {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Set<ConstraintViolation<Event>> violations = factory.getValidator().validate(event);
      if (violations.isEmpty()) {
        return;
      }
      throw new ConstraintViolationException(violations);
    }
  }
}
