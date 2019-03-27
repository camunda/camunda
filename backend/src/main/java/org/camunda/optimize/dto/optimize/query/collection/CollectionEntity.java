package org.camunda.optimize.dto.optimize.query.collection;

import java.time.OffsetDateTime;

public interface CollectionEntity {

  String getId();

  OffsetDateTime getLastModified();
}
