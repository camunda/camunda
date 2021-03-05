/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
@FieldNameConstants
public class EventSourceEntryDtoOld {

  private String id;
  private EventSourceType type;
  private List<EventScopeType> eventScope;
  private String processDefinitionKey;
  private List<String> versions;
  private List<String> tenants;
  private boolean tracedByBusinessKey;
  private String traceVariable;

}
