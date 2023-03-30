/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.service;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessService.class);

  @Autowired private ZeebeClient zeebeClient;

  public ProcessInstanceDTO startProcessInstance(final String processDefinitionKey) {
    return startProcessInstance(processDefinitionKey, null);
  }

  public ProcessInstanceDTO startProcessInstance(
      final String processDefinitionKey, final String payload) {

    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            zeebeClient
                .newCreateInstanceCommand()
                .bpmnProcessId(processDefinitionKey)
                .latestVersion();

    if (payload != null) {
      createProcessInstanceCommandStep3.variables(payload);
    }

    ProcessInstanceEvent processInstanceEvent = null;
    try {
      processInstanceEvent = createProcessInstanceCommandStep3.send().join();
      LOGGER.debug("Process instance created for process [{}]", processDefinitionKey);
    } catch (ClientStatusException ex) {
      if (Status.Code.NOT_FOUND.equals(ex.getStatusCode())) {
        throw new NotFoundException(
            String.format(
                "No process definition found with processDefinitionKey: '%s'",
                processDefinitionKey),
            ex);
      }
      throw new TasklistRuntimeException(ex.getMessage(), ex);
    } catch (ClientException ex) {
      throw new TasklistRuntimeException(ex.getMessage(), ex);
    }

    return new ProcessInstanceDTO().setId(processInstanceEvent.getProcessInstanceKey());
  }
}
