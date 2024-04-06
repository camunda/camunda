/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorizationService;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.grpc.Status;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessService.class);

  @Autowired private ZeebeClient zeebeClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TenantService tenantService;

  @Autowired private IdentityAuthorizationService identityAuthorizationService;

  public ProcessInstanceDTO startProcessInstance(
      final String processDefinitionKey, final String tenantId) {
    return startProcessInstance(processDefinitionKey, null, tenantId);
  }

  public ProcessInstanceDTO startProcessInstance(
      final String processDefinitionKey,
      final List<VariableInputDTO> variables,
      final String tenantId) {

    if (!identityAuthorizationService.isAllowedToStartProcess(processDefinitionKey)) {
      throw new ForbiddenActionException(
          "User does not have the permission to start this process.");
    }

    final boolean isMultiTenancyEnabled = tenantService.isMultiTenancyEnabled();

    if (isMultiTenancyEnabled && !tenantService.getAuthenticatedTenants().contains(tenantId)) {
      throw new InvalidRequestException("Invalid tenant.");
    }

    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            zeebeClient
                .newCreateInstanceCommand()
                .bpmnProcessId(processDefinitionKey)
                .latestVersion();

    if (isMultiTenancyEnabled) {
      createProcessInstanceCommandStep3.tenantId(tenantId);
    }

    if (CollectionUtils.isNotEmpty(variables)) {
      final Map<String, Object> variablesMap =
          variables.stream()
              .collect(Collectors.toMap(VariableInputDTO::getName, this::extractTypedValue));
      createProcessInstanceCommandStep3.variables(variablesMap);
    }

    ProcessInstanceEvent processInstanceEvent = null;
    try {
      processInstanceEvent = createProcessInstanceCommandStep3.send().join();
      LOGGER.debug("Process instance created for process [{}]", processDefinitionKey);
    } catch (ClientStatusException ex) {
      if (Status.Code.NOT_FOUND.equals(ex.getStatusCode())) {
        throw new NotFoundApiException(
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
    if (variable.getValue().equals("null")) {
      return objectMapper
          .nullNode(); // JSON Object null must be instanced like "null", also should not send to
      // objectMapper null values
    }

    try {
      return objectMapper.readValue(variable.getValue(), Object.class);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }
}
