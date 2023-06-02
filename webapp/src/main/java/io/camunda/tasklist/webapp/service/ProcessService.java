/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.service;

import static java.util.Objects.requireNonNullElse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.grpc.Status;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessService.class);

  @Autowired private ZeebeClient zeebeClient;

  @Autowired private ObjectMapper objectMapper;

  public ProcessInstanceDTO startProcessInstance(final String processDefinitionKey) {
    return startProcessInstance(processDefinitionKey, null);
  }

  public ProcessInstanceDTO startProcessInstance(
      final String processDefinitionKey, final List<VariableInputDTO> variables) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            zeebeClient
                .newCreateInstanceCommand()
                .bpmnProcessId(processDefinitionKey)
                .latestVersion();

    if (variables != null && variables.size() > 0) {

      final Map<String, Object> variablesMap = new HashMap<>();

      requireNonNullElse(variables, Collections.<VariableInputDTO>emptyList())
          .forEach(
              variable -> {
                variablesMap.put(variable.getName(), this.extractTypedValue(variable));
              });

      createProcessInstanceCommandStep3.variables(variablesMap);
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

  private Object extractTypedValue(VariableInputDTO variable) {
    try {
      return objectMapper.readValue(variable.getValue(), Object.class);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }
}
