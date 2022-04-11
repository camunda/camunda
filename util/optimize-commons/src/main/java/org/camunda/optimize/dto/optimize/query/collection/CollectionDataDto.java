/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@FieldNameConstants(asEnum = true)
public class CollectionDataDto {
  protected Object configuration = new HashMap<>();
  private List<CollectionRoleRequestDto> roles = new ArrayList<>();
  private List<CollectionScopeEntryDto> scope = new ArrayList<>();
}
