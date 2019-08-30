/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.schema.version25dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Version25CollectionDefinitionDto {

  protected String id;
  protected String name;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;
  protected Version25CollectionDataDto data = new Version25CollectionDataDto();
}
