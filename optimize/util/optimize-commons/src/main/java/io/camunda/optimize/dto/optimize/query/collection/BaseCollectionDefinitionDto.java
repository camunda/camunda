/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class BaseCollectionDefinitionDto<DATA_TYPE> {

  protected String id;
  protected String name;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;
  protected DATA_TYPE data;
  protected boolean automaticallyCreated = false;

  public enum Fields {
    id,
    name,
    lastModified,
    created,
    owner,
    lastModifier,
    data,
    automaticallyCreated
  }
}
