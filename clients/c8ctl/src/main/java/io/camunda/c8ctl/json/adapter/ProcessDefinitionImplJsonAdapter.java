/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c8ctl.json.adapter;

import io.avaje.jsonb.Jsonb;
import io.camunda.client.impl.search.response.ProcessDefinitionImpl;

public class ProcessDefinitionImplJsonAdapter extends AbstractJsonAdapter<ProcessDefinitionImpl> {

  public ProcessDefinitionImplJsonAdapter(final Jsonb jsonb) {
    super(jsonb);
  }

  @Override
  protected void addFields() {
    addField("name", String.class, ProcessDefinitionImpl::getName);
    addField("processDefinitionId", String.class, ProcessDefinitionImpl::getProcessDefinitionId);
    addField("version", Integer.class, ProcessDefinitionImpl::getVersion);
    addField("resourceName", String.class, ProcessDefinitionImpl::getResourceName);
    addField("tenantId", String.class, ProcessDefinitionImpl::getTenantId);
    addField("processDefinitionKey", Long.class, ProcessDefinitionImpl::getProcessDefinitionKey);
  }

  @Override
  protected Class<ProcessDefinitionImpl> getResourceClass() {
    return ProcessDefinitionImpl.class;
  }
}
