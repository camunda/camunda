/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import org.camunda.optimize.dto.optimize.query.entity.EntityDto;

import java.time.OffsetDateTime;

public interface CollectionEntity {

  String getId();

  String getCollectionId();

  String getName();

  OffsetDateTime getLastModified();

  EntityDto toEntityDto();
}
