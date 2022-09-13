/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;

import java.time.OffsetDateTime;

public interface CollectionEntity {

  String getId();

  String getCollectionId();

  String getName();

  String getOwner();

  OffsetDateTime getLastModified();

  EntityResponseDto toEntityDto(final RoleType roleType);
}
