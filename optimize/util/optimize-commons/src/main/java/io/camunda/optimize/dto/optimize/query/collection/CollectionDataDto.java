/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class CollectionDataDto {

  protected Object configuration = new HashMap<>();
  private List<CollectionRoleRequestDto> roles = new ArrayList<>();
  private List<CollectionScopeEntryDto> scope = new ArrayList<>();

  public enum Fields {
    configuration,
    roles,
    scope
  }
}
