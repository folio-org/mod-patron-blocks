package org.folio.domain.event;

import java.util.UUID;

import org.folio.domain.Event;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@JsonInclude(JsonInclude.Include.NON_NULL)
@RequiredArgsConstructor
@Getter
@Builder
@ToString(exclude = "data")
public class DomainEvent<T extends Event> {
  private final UUID id;
  private final String tenant;
  private final long timestamp;
  private final T data;
}
