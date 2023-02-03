/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.mutation;

import graphql.kickstart.tools.GraphQLMutationResolver;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.es.TaskReaderWriter;
import io.camunda.tasklist.webapp.es.cache.ProcessReader;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessMutationResolver implements GraphQLMutationResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessMutationResolver.class);

  @Autowired private ZeebeClient zeebeClient;

  @Autowired private ProcessReader processReader;

  @Autowired private TaskReaderWriter taskReaderWriter;

  public ZeebeClient getZeebeClient() {
    return zeebeClient;
  }

  public void setZeebeClient(final ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  public ProcessInstanceDTO startProcess(final String processDefinitionId) {
    return this.startProcess(processDefinitionId, null);
  }

  public ProcessInstanceDTO startProcess(final String processDefinitionId, final String payload) {

    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            zeebeClient
                .newCreateInstanceCommand()
                .bpmnProcessId(processDefinitionId)
                .latestVersion();

    if (payload != null) {
      createProcessInstanceCommandStep3.variables(payload);
    }

    ProcessInstanceEvent processInstanceEvent = null;
    try {
      processInstanceEvent = createProcessInstanceCommandStep3.send().join();
      LOGGER.debug("Process instance created for process [{}]", processDefinitionId);
    } catch (ClientException ex) {
      throw new TasklistRuntimeException(ex.getMessage());
    }

    final ProcessInstanceDTO processInstance =
        new ProcessInstanceDTO().setId(processInstanceEvent.getProcessInstanceKey());
    return processInstance;
  }
}
