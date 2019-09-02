/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data()
@EqualsAndHashCode(callSuper = true)
public class ResolvedCollectionDataDto extends CollectionDataDto {
  protected List<CollectionEntity> entities = new ArrayList<>();
}
