/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.query;

import static io.camunda.zeebe.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;

import graphql.kickstart.tools.GraphQLQueryResolver;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.webapp.graphql.entity.ProcessDTO;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorizationService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessQueryResolver implements GraphQLQueryResolver {

  @Autowired private ProcessStore processStore;

  @Autowired private IdentityAuthorizationService identityAuthorizationService;

  public List<ProcessDTO> getProcesses(String search) {
    return processStore
        .getProcesses(
            search,
            identityAuthorizationService.getProcessDefinitionsFromAuthorization(),
            DEFAULT_TENANT_IDENTIFIER)
        .stream()
        .map(ProcessDTO::createFrom)
        .collect(Collectors.toList());
  }
}
