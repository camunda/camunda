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
import io.camunda.process.test.api.mock.BpmnExampleDataReader;
import io.camunda.process.test.impl.assertions.util.CamundaAssertJsonMapper;
import io.camunda.process.test.impl.assertions.util.CamundaAssertJsonMapper.JsonMappingException;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

public class BpmnExampleDataReaderImpl implements BpmnExampleDataReader {

  private static final Duration GET_PROCESS_DEFINITION_XML_TIMEOUT = Duration.ofSeconds(10);
  private static final String EXAMPLE_DATA_ATTRIBUTE_NAME = "camundaModeler:exampleOutputJson";

  private final CamundaClient client;
  private final CamundaAssertJsonMapper jsonMapper;

  public BpmnExampleDataReaderImpl(
      final CamundaClient client, final CamundaAssertJsonMapper jsonMapper) {

    this.client = client;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Map<String, Object> readExampleData(
      final long processDefinitionKey, final String elementId) {

    final String failureMessage =
        String.format(
            "BPMN Model [processDefinitionKey: '%s', elementId: '%s']",
            processDefinitionKey, elementId);

    final ModelElementInstance elementModelInstance =
        buildModelInstance(failureMessage, processDefinitionKey).getModelElementById(elementId);

    return extractVariablesFromExampleData(failureMessage, elementModelInstance);
  }

  private BpmnModelInstance buildModelInstance(
      final String failureMessagePrefix, final long processDefinitionKey) {

    final AtomicReference<BpmnModelInstance> modelInstance = new AtomicReference<>();

    try {
      Awaitility.await()
          .atMost(GET_PROCESS_DEFINITION_XML_TIMEOUT)
          .ignoreExceptions()
          .until(
              () -> {
                final String bpmnXml =
                    client.newProcessDefinitionGetXmlRequest(processDefinitionKey).send().join();

                modelInstance.set(
                    Bpmn.readModelFromStream(new ByteArrayInputStream(bpmnXml.getBytes())));
                return true;
              });

    } catch (final Throwable t) {

      final String errorMessage =
          String.format("%s failed to parse the BPMN model", failureMessagePrefix);
      throw new BpmnExampleDataReadException(errorMessage, t);
    }

    return modelInstance.get();
  }

  private Map<String, Object> extractVariablesFromExampleData(
      final String failureMessagePrefix, final ModelElementInstance exampleDataParentElement) {

    final ZeebeProperty examplePropertyNode =
        exampleDataParentElement
            .getModelInstance()
            .getModelElementsByType(ZeebeProperty.class)
            .stream()
            .filter(property -> EXAMPLE_DATA_ATTRIBUTE_NAME.equalsIgnoreCase(property.getName()))
            .findFirst()
            .orElseThrow(
                () -> {
                  final String errorMessage =
                      String.format(
                          "%s has no example data for the given element-id. Example data must have the "
                              + "attribute name '%s' or else it won't be recognized.",
                          failureMessagePrefix, EXAMPLE_DATA_ATTRIBUTE_NAME);
                  return new BpmnExampleDataReadException(errorMessage);
                });

    try {
      return jsonMapper.fromJsonAsMap(examplePropertyNode.getValue());
    } catch (final JsonMappingException e) {

      final String failureMessage =
          String.format(
              "%s failed to parse the example data '%s'",
              failureMessagePrefix, examplePropertyNode.getValue());
      throw new BpmnExampleDataReadException(failureMessage, e);
    }
  }
}
