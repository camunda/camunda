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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockChildProcessBuilderImpl implements MockChildProcessBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(MockChildProcessBuilderImpl.class);

  private final String childProcessId;
  private final CamundaClient client;
  private String versionTag;

  public MockChildProcessBuilderImpl(final String childProcessId, final CamundaClient client) {
    this.childProcessId = childProcessId;
    this.client = client;
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
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(childProcessId);

    if (versionTag != null) {
      processBuilder.versionTag(versionTag);
    }

    final BpmnModelInstance processModel =
        processBuilder
            .startEvent()
            .endEvent(
                "child-end",
                e ->
                    variables.forEach(
                        (k, v) ->
                            e.zeebeOutput(
                                "=" + client.getConfiguration().getJsonMapper().toJson(v), k)))
            .done();

    LOGGER.debug(
        "Mock: Deploy a child process '{}' with version tag '{}' and result variables {}",
        childProcessId,
        versionTag,
        variables);

    final String resourceName = childProcessId + ".bpmn";
    client.newDeployResourceCommand().addProcessModel(processModel, resourceName).send().join();
  }
}
