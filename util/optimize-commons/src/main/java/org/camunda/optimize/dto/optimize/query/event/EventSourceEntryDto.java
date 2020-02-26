/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.service.util.IdGenerator;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@FieldNameConstants
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EventSourceEntryDto {

  @EqualsAndHashCode.Include
  @NonNull
  @Builder.Default
  private String id = IdGenerator.getNextId();
  @Builder.Default
  private EventSourceType type = EventSourceType.CAMUNDA;
  @Builder.Default
  private EventScopeType eventScope = EventScopeType.ALL;

  // camunda source specific properties
  private String processDefinitionKey;
  @Builder.Default
  private List<String> versions = new ArrayList<>();
  @Builder.Default
  private List<String> tenants = new ArrayList<>();
  private Boolean tracedByBusinessKey;
  private String traceVariable;

}
