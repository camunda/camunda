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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

public class BpmnExampleDataReader {

  private final CamundaClient client;
  private final CamundaAssertAwaitBehavior awaitBehavior;

  public BpmnExampleDataReader(
      final CamundaClient client, final CamundaAssertAwaitBehavior awaitBehavior) {

    this.client = client;
    this.awaitBehavior = awaitBehavior;
  }

  public String readExampleData(
      final long processDefinitionKey, final String processId, final String elementId) {

    final String failureMessagePrefix =
        String.format("BPMN Model [processId: '%s', elementId: '%s']", processId, elementId);

    return Optional.<ModelElementInstance>ofNullable(
            buildModelInstance(failureMessagePrefix, processDefinitionKey)
                .getModelElementById(elementId))
        .map(parentElement -> queryParentElementForExampleData(failureMessagePrefix, parentElement))
        .orElseThrow(
            () -> {
              final String failureMessage =
                  String.format(
                      "%s does not contain element with id '%s'", failureMessagePrefix, elementId);
              return new BpmnExampleDataReaderException(failureMessage);
            });
  }

  private BpmnModelInstance buildModelInstance(
      final String failureMessagePrefix, final long processDefinitionKey) {

    try {
      return awaitBehavior.until(
          () ->
              Optional.ofNullable(
                      client.newProcessDefinitionGetXmlRequest(processDefinitionKey).send().join())
                  .map(response -> response.getBytes(StandardCharsets.UTF_8))
                  .map(ByteArrayInputStream::new)
                  .map(Bpmn::readModelFromStream)
                  .orElse(null),
          process ->
              assertThat(process)
                  .withFailMessage("%s has no BPMN model available.", failureMessagePrefix)
                  .isNotNull());
    } catch (final AssertionError e) {
      throw new BpmnExampleDataReaderException(
          String.format("%s failed to parse the BPMN model.", failureMessagePrefix), e);
    }
  }

  private String queryParentElementForExampleData(
      final String failureMessagePrefix, final ModelElementInstance parentElement) {

    return queryParentElementForExampleData(parentElement)
        .orElseThrow(
            () -> {
              final String failureMessage =
                  String.format(
                      "%s has no example data as a property with the name '%s'.",
                      failureMessagePrefix, ZeebeConstants.PROPERTY_EXAMPLE_DATA);
              return new BpmnExampleDataReaderException(failureMessage);
            });
  }

  private Optional<String> queryParentElementForExampleData(
      final ModelElementInstance parentElement) {

    return ((BaseElement) parentElement)
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(ZeebeProperties.class)
            .list()
            .stream()
            .flatMap(props -> props.getProperties().stream())
            .filter(prop -> ZeebeConstants.PROPERTY_EXAMPLE_DATA.equals(prop.getName()))
            .findFirst()
            .map(ZeebeProperty::getValue)
            .filter(Objects::nonNull);
  }

  public static class BpmnExampleDataReaderException extends RuntimeException {

    public BpmnExampleDataReaderException(final String message) {
      super(message);
    }

    public BpmnExampleDataReaderException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
