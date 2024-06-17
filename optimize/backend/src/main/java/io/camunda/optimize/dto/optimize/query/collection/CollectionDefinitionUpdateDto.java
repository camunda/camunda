/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class CollectionDefinitionUpdateDto {

  protected String name;
  protected OffsetDateTime lastModified;
  protected String owner;
  protected String lastModifier;

  protected PartialCollectionDataDto data;
}
