/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.mutation;

import graphql.kickstart.tools.GraphQLMutationResolver;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.service.ProcessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
public class ProcessMutationResolver implements GraphQLMutationResolver {
  @Autowired private ProcessService processService;

  @PreAuthorize("hasPermission('write')")
  public ProcessInstanceDTO startProcess(final String processDefinitionId) {
    return processService.startProcessInstance(processDefinitionId);
  }

  @PreAuthorize("hasPermission('write')")
  public ProcessInstanceDTO startProcess(final String processDefinitionId, final String payload) {
    return processService.startProcessInstance(processDefinitionId, payload);
  }
}
