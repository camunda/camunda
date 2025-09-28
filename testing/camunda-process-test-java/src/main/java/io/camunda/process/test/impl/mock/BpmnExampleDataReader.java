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
import io.camunda.process.test.impl.assertions.util.CamundaAssertJsonMapper;
import io.camunda.process.test.impl.assertions.util.CamundaAssertJsonMapper.JsonMappingException;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.awaitility.core.ConditionTimeoutException;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BpmnExampleDataReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(BpmnExampleDataReader.class);

  private static final Pattern EMPTY_JSON_PATTERN = Pattern.compile("\\s*\\{\\s*}\\s*");
  private static final String EMPTY_JSON_OBJECT = "{}";

  private final CamundaClient client;
  private final CamundaAssertJsonMapper jsonMapper;
  private final CamundaAssertAwaitBehavior awaitBehavior;

  public BpmnExampleDataReader(
      final CamundaClient client,
      final CamundaAssertAwaitBehavior awaitBehavior,
      final CamundaAssertJsonMapper jsonMapper) {

    this.client = client;
    this.awaitBehavior = awaitBehavior;
    this.jsonMapper = jsonMapper;
  }

  public Map<String, Object> readExampleDataAsMap(
      final long processDefinitionKey, final String elementId) {

    return jsonMapper.fromJsonAsMap(readExampleData(processDefinitionKey, elementId));
  }

  public String readExampleData(final long processDefinitionKey, final String elementId) {
    final String failureMessagePrefix =
        String.format(
            "BPMN Model [processDefinitionKey: '%s', elementId: '%s']",
            processDefinitionKey, elementId);

    return Optional.<ModelElementInstance>ofNullable(
            buildModelInstance(failureMessagePrefix, processDefinitionKey)
                .getModelElementById(elementId))
        .map(parentElement -> queryParentElementForExampleData(failureMessagePrefix, parentElement))
        .orElseThrow(
            () -> {
              final String failureMessage =
                  String.format(
                      "%s does not contain element with id '%s'", failureMessagePrefix, elementId);
              return new NoSuchBpmnElementException(failureMessage);
            });
  }

  private BpmnModelInstance buildModelInstance(
      final String failureMessagePrefix, final long processDefinitionKey) {

    final AtomicReference<BpmnModelInstance> modelInstance = new AtomicReference<>();

    try {
      awaitBehavior.untilAsserted(
          () -> {
            final Optional<String> bpmnXml =
                Optional.ofNullable(
                    client.newProcessDefinitionGetXmlRequest(processDefinitionKey).send().join());

            assertThat(bpmnXml).isPresent();

            modelInstance.set(
                Bpmn.readModelFromStream(new ByteArrayInputStream(bpmnXml.get().getBytes())));
          });
    } catch (final ConditionTimeoutException t) {
      throw new FailedToParseBpmnModelException(
          String.format("%s failed to parse the BPMN model.", failureMessagePrefix), t);
    } catch (final Throwable t) {
      System.out.println("Noooo.");
    }

    return modelInstance.get();
  }

  private String queryParentElementForExampleData(
      final String failureMessagePrefix, final ModelElementInstance parentElement) {

    return queryParentElementForExampleData(parentElement)
        .filter(exampleData -> validateExampleData(failureMessagePrefix, exampleData))
        .orElseGet(
            () -> {
              LOGGER.warn(
                  "{} has no example data. Example data must have the attribute name '{}' or "
                      + "else it won't be recognized. Returning an empty JSON object.",
                  failureMessagePrefix,
                  ZeebeConstants.ATTRIBUTE_EXAMPLE_DATA);
              return EMPTY_JSON_OBJECT;
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
            .filter(prop -> ZeebeConstants.ATTRIBUTE_EXAMPLE_DATA.equals(prop.getName()))
            .findFirst()
            .map(ZeebeProperty::getValue)
            .filter(Objects::nonNull);
  }

  private boolean validateExampleData(final String failureMessagePrefix, final String exampleData) {
    final String trimmedExampleData = exampleData.trim();

    if (trimmedExampleData.isEmpty() || isEmptyJsonObject(trimmedExampleData)) {
      LOGGER.warn("{} has an empty example data object.", failureMessagePrefix);
      return false;
    }

    try {
      jsonMapper.readJson(trimmedExampleData);
      return true;
    } catch (final JsonMappingException e) {
      throw new InvalidExampleDataJsonException(
          String.format(
              "%s has invalid JSON example data '%s'", failureMessagePrefix, trimmedExampleData),
          e);
    }
  }

  private boolean isEmptyJsonObject(final String exampleData) {
    return EMPTY_JSON_PATTERN.matcher(exampleData).find();
  }

  public static class NoSuchBpmnElementException extends RuntimeException {

    public NoSuchBpmnElementException(final String message) {
      super(message);
    }

    public NoSuchBpmnElementException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  public static class FailedToParseBpmnModelException extends RuntimeException {

    public FailedToParseBpmnModelException(final String message) {
      super(message);
    }

    public FailedToParseBpmnModelException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  public static class InvalidExampleDataJsonException extends RuntimeException {

    public InvalidExampleDataJsonException(final String message) {
      super(message);
    }

    public InvalidExampleDataJsonException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
