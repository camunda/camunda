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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.process.test.impl.assertions.util.AwaitilityBehavior;
import io.camunda.process.test.impl.assertions.util.CamundaAssertJsonMapper;
import io.camunda.process.test.impl.mock.BpmnExampleDataReader.InvalidExampleDataJsonException;
import io.camunda.process.test.impl.mock.BpmnExampleDataReader.NoSuchBpmnElementException;
import io.camunda.process.test.utils.CamundaAssertExpectFailure;
import io.camunda.process.test.utils.CamundaAssertExtension;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({CamundaAssertExtension.class, MockitoExtension.class})
public class BpmnExampleDataReaderTest {

  private static final long PROCESS_DEFINITION_KEY = 1L;

  private BpmnExampleDataReader reader;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient client;

  private final CamundaAssertJsonMapper jsonMapper =
      new CamundaAssertJsonMapper(new CamundaObjectMapper());

  @BeforeEach
  public void setup() {
    reader = new BpmnExampleDataReader(client, new AwaitilityBehavior(), jsonMapper);
  }

  @Test
  public void successfulRead() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnModelWithExampleData());

    // when
    final Map<String, Object> actual =
        reader.readExampleDataAsMap(PROCESS_DEFINITION_KEY, "task_send_email");

    // then
    final Map<String, Object> expected =
        Collections.singletonMap(
            "response", Collections.singletonMap("body", Collections.singletonMap("status", 200)));

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void readEventuallySuccessful() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(bpmnModelWithExampleData());

    // when
    final Map<String, Object> actual =
        reader.readExampleDataAsMap(PROCESS_DEFINITION_KEY, "task_send_email");

    // then
    final Map<String, Object> expected =
        Collections.singletonMap(
            "response", Collections.singletonMap("body", Collections.singletonMap("status", 200)));

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void noExampleDataFoundReturnsEmptyVariablesObject() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnModelWithoutExampleData());

    // when/then
    final Map<String, Object> actualMap =
        reader.readExampleDataAsMap(PROCESS_DEFINITION_KEY, "task_send_email");
    final String actualJson = reader.readExampleData(PROCESS_DEFINITION_KEY, "task_send_email");

    assertThat(actualMap).isEmpty();
    assertThat(actualJson).isEqualTo("{}");
  }

  @Test
  public void emptyExampleDataReturnsEmptyVariablesObject() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnModelWithEmptyExampleData());

    // when/then
    final Map<String, Object> actualMap =
        reader.readExampleDataAsMap(PROCESS_DEFINITION_KEY, "task_send_email");
    final String actualJson = reader.readExampleData(PROCESS_DEFINITION_KEY, "task_send_email");

    assertThat(actualMap).isEmpty();
    assertThat(actualJson).isEqualTo("{}");
  }

  @Test
  @CamundaAssertExpectFailure
  public void noSuchElementFound() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnModelWithoutExampleData());

    // when/then
    assertThatThrownBy(() -> reader.readExampleData(PROCESS_DEFINITION_KEY, "task_missing"))
        .isInstanceOf(NoSuchBpmnElementException.class)
        .hasMessageContaining("BPMN Model [processDefinitionKey: '1', elementId: 'task_missing']")
        .hasMessageContaining("does not contain element with id 'task_missing'");

    assertThatThrownBy(() -> reader.readExampleDataAsMap(PROCESS_DEFINITION_KEY, "task_missing"))
        .isInstanceOf(NoSuchBpmnElementException.class)
        .hasMessageContaining("BPMN Model [processDefinitionKey: '1', elementId: 'task_missing']")
        .hasMessageContaining("does not contain element with id 'task_missing'");
  }

  @Test
  @CamundaAssertExpectFailure
  public void malformedExampleData() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnModelWithMalformedExampleData());

    // when/then
    final String expectedFailureMessage =
        "BPMN Model [processDefinitionKey: '1', elementId: 'task_send_email'] has invalid JSON example data 'this is not valid json'";

    assertThatThrownBy(() -> reader.readExampleDataAsMap(PROCESS_DEFINITION_KEY, "task_send_email"))
        .isInstanceOf(InvalidExampleDataJsonException.class)
        .hasMessageContaining(expectedFailureMessage);

    assertThatThrownBy(() -> reader.readExampleData(PROCESS_DEFINITION_KEY, "task_send_email"))
        .isInstanceOf(InvalidExampleDataJsonException.class)
        .hasMessageContaining(expectedFailureMessage);
  }

  @Test
  public void emptyExampleData() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnModelWithEmptyExampleDataAttribute());

    // when/then
    final Map<String, Object> actualMap =
        reader.readExampleDataAsMap(PROCESS_DEFINITION_KEY, "service_task_review_email_c");
    final String actualJson =
        reader.readExampleData(PROCESS_DEFINITION_KEY, "service_task_review_email_c");

    assertThat(actualMap).isEmpty();
    assertThat(actualJson).isEqualTo("{}");
  }

  private String bpmnModelWithEmptyExampleDataAttribute() {
    return bpmnModel("/mockJobWorker/complex-send-email-with-example.bpmn");
  }

  private String bpmnModelWithExampleData() {
    return bpmnModel("/mockJobWorker/send-email-with-example.bpmn");
  }

  private String bpmnModelWithEmptyExampleData() {
    return bpmnModel("/mockJobWorker/send-email-with-empty-example.bpmn");
  }

  private String bpmnModelWithoutExampleData() {
    return bpmnModel("/mockJobWorker/send-email-without-example.bpmn");
  }

  private String bpmnModelWithMalformedExampleData() {
    return bpmnModel("/mockJobWorker/send-email-with-malformed-example.bpmn");
  }

  private String bpmnModel(final String resourcePath) {
    try {
      return new String(
          Files.readAllBytes(Paths.get(getClass().getResource(resourcePath).toURI())));
    } catch (final Throwable t) {
      throw new RuntimeException("Unable to read file " + resourcePath, t);
    }
  }
}
