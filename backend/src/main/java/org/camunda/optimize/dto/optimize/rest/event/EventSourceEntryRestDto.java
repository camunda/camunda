/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EventSourceEntryRestDto {
  @EqualsAndHashCode.Include
  private String id;
  private String processDefinitionKey;
  private String processDefinitionName;
  private List<String> versions = new ArrayList<>();
  private List<String> tenants = new ArrayList<>();
  private Boolean tracedByBusinessKey;
  private String traceVariable;
  private EventScopeType eventScope;
}
