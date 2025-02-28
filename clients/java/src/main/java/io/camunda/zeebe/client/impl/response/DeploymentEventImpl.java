/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.client.impl.response;

import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.DeploymentDecisionRequirementsResult;
import io.camunda.client.protocol.rest.DeploymentDecisionResult;
import io.camunda.client.protocol.rest.DeploymentFormResult;
import io.camunda.client.protocol.rest.DeploymentMetadataResult;
import io.camunda.client.protocol.rest.DeploymentProcessResult;
import io.camunda.client.protocol.rest.DeploymentResult;
import io.camunda.zeebe.client.api.command.CommandWithTenantStep;
import io.camunda.zeebe.client.api.response.Decision;
import io.camunda.zeebe.client.api.response.DecisionRequirements;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.Form;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.impl.Loggers;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployProcessResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Deployment;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;

public final class DeploymentEventImpl implements DeploymentEvent {

  private static final Logger LOG = Loggers.LOGGER;
  private static final String UNKNOWN_METADATA_WARN_MSG =
      "Expected metadata in deployment response, but encountered an unknown type of metadata."
          + " This might happen when you've updated your Zeebe cluster, but not your Zeebe client."
          + " You may have to update the version of your zeebe-client-java dependency to resolve the issue.";

  private final long key;
  private final String tenantId;
  private final List<Process> processes = new ArrayList<>();
  private final List<Decision> decisions = new ArrayList<>();
  private final List<DecisionRequirements> decisionRequirements = new ArrayList<>();
  private final List<Form> forms = new ArrayList<>();

  public DeploymentEventImpl(final DeployProcessResponse response) {
    key = response.getKey();
    tenantId = CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;
    response.getProcessesList().stream().map(ProcessImpl::new).forEach(processes::add);
  }

  public DeploymentEventImpl(final DeployResourceResponse response) {
    key = response.getKey();
    tenantId = response.getTenantId();
    for (final Deployment deployment : response.getDeploymentsList()) {
      switch (deployment.getMetadataCase()) {
        case PROCESS:
          processes.add(new ProcessImpl(deployment.getProcess()));
          break;
        case DECISION:
          decisions.add(new DecisionImpl(deployment.getDecision()));
          break;
        case DECISIONREQUIREMENTS:
          decisionRequirements.add(
              new DecisionRequirementsImpl(deployment.getDecisionRequirements()));
          break;
        case FORM:
          forms.add(new FormImpl(deployment.getForm()));
          break;
        case METADATA_NOT_SET:
        default:
          LOG.warn(UNKNOWN_METADATA_WARN_MSG);
          break;
      }
    }
  }

  public DeploymentEventImpl(final DeploymentResult response) {
    key = ParseUtil.parseLongOrEmpty(response.getDeploymentKey());
    tenantId = response.getTenantId();

    for (final DeploymentMetadataResult deployment : response.getDeployments()) {
      addDeployedForm(deployment.getForm());
      addDeployedProcess(deployment.getProcessDefinition());
      addDeployedDecision(deployment.getDecisionDefinition());
      addDeployedDecisionRequirements(deployment.getDecisionRequirements());
    }
  }

  private void addDeployedForm(final DeploymentFormResult form) {
    Optional.ofNullable(form)
        .ifPresent(
            f ->
                forms.add(
                    new FormImpl(
                        f.getFormId(),
                        f.getVersion(),
                        ParseUtil.parseLongOrEmpty(f.getFormKey()),
                        f.getResourceName(),
                        f.getTenantId())));
  }

  private void addDeployedDecisionRequirements(
      final DeploymentDecisionRequirementsResult decisionRequirement) {
    Optional.ofNullable(decisionRequirement)
        .ifPresent(
            dr ->
                decisionRequirements.add(
                    new DecisionRequirementsImpl(
                        dr.getDecisionRequirementsId(),
                        dr.getDecisionRequirementsName(),
                        dr.getVersion(),
                        ParseUtil.parseLongOrEmpty(dr.getDecisionRequirementsKey()),
                        dr.getResourceName(),
                        dr.getTenantId())));
  }

  private void addDeployedDecision(final DeploymentDecisionResult decision) {
    Optional.ofNullable(decision)
        .ifPresent(
            d ->
                decisions.add(
                    new DecisionImpl(
                        d.getDecisionDefinitionId(),
                        d.getName(),
                        d.getVersion(),
                        ParseUtil.parseLongOrEmpty(d.getDecisionDefinitionKey()),
                        d.getDecisionRequirementsId(),
                        ParseUtil.parseLongOrEmpty(d.getDecisionRequirementsKey()),
                        d.getTenantId())));
  }

  private void addDeployedProcess(final DeploymentProcessResult process) {
    Optional.ofNullable(process)
        .ifPresent(
            p ->
                processes.add(
                    new ProcessImpl(
                        ParseUtil.parseLongOrEmpty(p.getProcessDefinitionKey()),
                        p.getProcessDefinitionId(),
                        p.getProcessDefinitionVersion(),
                        p.getResourceName(),
                        p.getTenantId())));
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public List<Process> getProcesses() {
    return processes;
  }

  @Override
  public List<Decision> getDecisions() {
    return decisions;
  }

  @Override
  public List<DecisionRequirements> getDecisionRequirements() {
    return decisionRequirements;
  }

  @Override
  public List<Form> getForm() {
    return forms;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String toString() {
    return "DeploymentEventImpl{"
        + "key="
        + key
        + ", processes="
        + processes
        + ", decisions="
        + decisions
        + ", decisionRequirements="
        + decisionRequirements
        + ", forms="
        + forms
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
