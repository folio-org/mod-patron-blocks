package org.folio.domain;

import org.folio.rest.jaxrs.model.Metadata;

public interface Event {
  String getId();
  String getUserId();
  Metadata getMetadata();
  void setMetadata(Metadata metadata);
}
