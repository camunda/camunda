/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c8ctl.json.adapter;

import io.avaje.jsonb.Jsonb;
import io.camunda.client.impl.search.response.ProcessInstanceImpl;

public class ProcessInstanceImplJsonAdapter extends AbstractJsonAdapter<ProcessInstanceImpl> {

  public ProcessInstanceImplJsonAdapter(final Jsonb jsonb) {
    super(jsonb);
  }

  @Override
  protected void addFields() {
    addField("processInstanceKey", Long.class, ProcessInstanceImpl::getProcessInstanceKey);
    addField("processDefinitionId", String.class, ProcessInstanceImpl::getProcessDefinitionId);
    addField("processDefinitionName", String.class, ProcessInstanceImpl::getProcessDefinitionName);
    addField(
        "processDefinitionVersion",
        Integer.class,
        ProcessInstanceImpl::getProcessDefinitionVersion);
    addField(
        "processDefinitionVersionTag",
        String.class,
        ProcessInstanceImpl::getProcessDefinitionVersionTag);
    addField("processDefinitionKey", Long.class, ProcessInstanceImpl::getProcessDefinitionKey);
    addField(
        "parentProcessInstanceKey", Long.class, ProcessInstanceImpl::getParentProcessInstanceKey);
    addField(
        "parentFlowNodeInstanceKey", Long.class, ProcessInstanceImpl::getParentFlowNodeInstanceKey);
    addField("startDate", String.class, ProcessInstanceImpl::getStartDate);
    addField("endDate", String.class, ProcessInstanceImpl::getEndDate);
    addField("state", String.class, ProcessInstanceImpl::getState);
    addField("hasIncident", Boolean.class, ProcessInstanceImpl::getHasIncident);
    addField("tenantId", String.class, ProcessInstanceImpl::getTenantId);
  }

  @Override
  protected Class<ProcessInstanceImpl> getResourceClass() {
    return ProcessInstanceImpl.class;
  }
}
