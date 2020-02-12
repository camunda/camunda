/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.event;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.EventSourceType;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EventSourceEntryRestDto {
  @EqualsAndHashCode.Include
  private String id;
  @Builder.Default
  private EventSourceType type = EventSourceType.CAMUNDA;
  @Builder.Default
  private EventScopeType eventScope = EventScopeType.ALL;
  private String processDefinitionKey;
  private String processDefinitionName;
  @Builder.Default
  private List<String> versions = new ArrayList<>();
  @Builder.Default
  private List<String> tenants = new ArrayList<>();
  private Boolean tracedByBusinessKey;
  private String traceVariable;
}
