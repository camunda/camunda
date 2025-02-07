/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c8ctl.json.adapter;

import io.avaje.jsonb.Jsonb;
import io.camunda.client.impl.search.response.UserTaskImpl;
import java.util.List;
import java.util.Map;

public class UserTaskImplJsonAdapter extends AbstractJsonAdapter<UserTaskImpl> {

  public UserTaskImplJsonAdapter(final Jsonb jsonb) {
    super(jsonb);
  }

  @Override
  protected void addFields() {
    addField("userTaskKey", Long.class, UserTaskImpl::getUserTaskKey);
    addField("name", String.class, UserTaskImpl::getName);
    addField("state", String.class, UserTaskImpl::getState);
    addField("assignee", String.class, UserTaskImpl::getAssignee);
    addField("elementId", String.class, UserTaskImpl::getElementId);
    addField("elementInstanceKey", Long.class, UserTaskImpl::getElementInstanceKey);
    addField("candidateGroup", List.class, UserTaskImpl::getCandidateGroups);
    addField("candidateUser", List.class, UserTaskImpl::getCandidateUsers);
    addField("bpmnProcessId", String.class, UserTaskImpl::getBpmnProcessId);
    addField("processDefinitionKey", Long.class, UserTaskImpl::getProcessDefinitionKey);
    addField("processInstanceKey", Long.class, UserTaskImpl::getProcessInstanceKey);
    addField("formKey", Long.class, UserTaskImpl::getFormKey);
    addField("creationDate", String.class, UserTaskImpl::getCreationDate);
    addField("completionDate", String.class, UserTaskImpl::getCompletionDate);
    addField("followUpDate", String.class, UserTaskImpl::getFollowUpDate);
    addField("dueDate", String.class, UserTaskImpl::getDueDate);
    addField("tenantId", String.class, UserTaskImpl::getTenantId);
    addField("externalFormReference", String.class, UserTaskImpl::getExternalFormReference);
    addField("processDefinitionVersion", Integer.class, UserTaskImpl::getProcessDefinitionVersion);
    addField("customHeaders", Map.class, UserTaskImpl::getCustomHeaders);
    addField("priority", Integer.class, UserTaskImpl::getPriority);
  }

  @Override
  protected Class<UserTaskImpl> getResourceClass() {
    return UserTaskImpl.class;
  }
}
