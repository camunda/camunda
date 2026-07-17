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
package io.camunda.process.test.impl.mock;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.mock.MockChildProcessBuilder;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockChildProcessBuilderImpl implements MockChildProcessBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(MockChildProcessBuilderImpl.class);

  private final CamundaClient client;
  private String childProcessId;
  private String versionTag;

  public MockChildProcessBuilderImpl(final CamundaClient client) {
    this.client = client;
  }

  @Override
  public MockChildProcessBuilder withProcessId(final String processId) {
    childProcessId = processId;
    return this;
  }

  @Override
  public MockChildProcessBuilder withVersionTag(final String versionTag) {
    this.versionTag = versionTag;
    return this;
  }

  @Override
  public void thenComplete() {
    thenComplete(Collections.emptyMap());
  }

  @Override
  public void thenComplete(final Map<String, Object> variables) {
    validateProcessId();

    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(childProcessId);

    if (versionTag != null) {
      processBuilder.versionTag(versionTag);
    }

    final Map<String, String> jsonVariables = toJsonVariables(variables);

    final BpmnModelInstance processModel =
        processBuilder
            .startEvent()
            .endEvent(
                "child-end",
                eventBuilder ->
                    jsonVariables.forEach(
                        (key, value) -> eventBuilder.zeebeOutputExpression(value, key)))
            .done();

    LOGGER.debug(
        "Mock: Deploy a child process '{}' with version tag '{}' and result variables {}",
        childProcessId,
        versionTag,
        variables);

    deploy(processModel);
  }

  @Override
  public void thenComplete(
      final Function<Map<String, Object>, Map<String, Object>> variablesSupplier) {
    validateProcessId();

    final String variableSupplierJobType = "variableSupplier_" + childProcessId;
    final BpmnModelInstance processModel =
        Bpmn.createExecutableProcess(childProcessId)
            .startEvent()
            .serviceTask("variableSupplier", t -> t.zeebeJobType(variableSupplierJobType))
            .endEvent()
            .done();

    LOGGER.debug("Mock: Deploy a child process '{}' with variables supplier", childProcessId);

    deploy(processModel);

    client
        .newWorker()
        .jobType(variableSupplierJobType)
        .handler(
            (jobClient, job) -> {
              final Map<String, Object> inputVariables = job.getVariablesAsMap();
              final Map<String, Object> outputVariables = variablesSupplier.apply(inputVariables);

              LOGGER.debug(
                  "Mock: Complete child process '{}' with variables {}",
                  childProcessId,
                  outputVariables);

              jobClient.newCompleteCommand(job.getKey()).variables(outputVariables).send().join();
            })
        .open();
  }

  private Map<String, String> toJsonVariables(final Map<String, Object> variables) {
    final Function<Entry<String, Object>, String> transformToJson =
        entry -> client.getConfiguration().getJsonMapper().toJson(entry.getValue());
    try {
      return variables.entrySet().stream()
          .collect(Collectors.toMap(Entry::getKey, transformToJson));
    } catch (final Exception ex) {
      throw new RuntimeException("Failed to build the mock process for the given variables", ex);
    }
  }

  private void validateProcessId() {
    if (childProcessId == null || childProcessId.trim().isEmpty()) {
      throw new IllegalStateException(
          "No process ID set. Use withProcessId(String) to set a process ID before calling thenComplete().");
    }
  }

  private void deploy(final BpmnModelInstance processModel) {
    final String resourceName = childProcessId + ".bpmn";
    client.newDeployResourceCommand().addProcessModel(processModel, resourceName).send().join();
  }
}
