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
import io.camunda.process.test.impl.assertions.util.AwaitilityBehavior;
import io.camunda.process.test.utils.CamundaAssertExtension;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({CamundaAssertExtension.class, MockitoExtension.class})
public class BpmnExampleDataReaderTest {

  private static final long PROCESS_DEFINITION_KEY = 1L;
  private static final String PROCESS_ID = "send_email_with_example_data";

  private BpmnExampleDataReader reader;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient client;

  private final Function<String, String> curriedReadExampleData =
      elementId -> reader.readExampleData(PROCESS_DEFINITION_KEY, PROCESS_ID, elementId);

  @BeforeEach
  public void setup() {
    reader = new BpmnExampleDataReader(client, new AwaitilityBehavior());
  }

  @Test
  public void successfulRead() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnModelWithExampleData());

    // when
    final String actual = curriedReadExampleData.apply("task_send_email");

    // then
    assertThat(actual).isEqualTo("{\"response\":{\"body\":{\"status\":200}}}");
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
    final String actual = curriedReadExampleData.apply("task_send_email");

    // then
    assertThat(actual).isEqualTo("{\"response\":{\"body\":{\"status\":200}}}");
  }

  @Test
  public void noExampleDataFoundThrowsException() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnModelWithoutExampleData());

    // when/then
    assertThatThrownBy(() -> curriedReadExampleData.apply("task_send_email"))
        .hasMessage(
            "BPMN Model [processId: '%s', elementId: 'task_send_email'] has no example data as a property with the name '%s'.",
            PROCESS_ID, ZeebeConstants.PROPERTY_EXAMPLE_DATA);
  }

  @Test
  public void emptyExampleDataReturnsEmptyVariablesObject() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnModelWithEmptyExampleData());

    // when
    final String actual = curriedReadExampleData.apply("task_send_email");

    // then
    assertThat(actual).isEqualTo("{}");
  }

  @Test
  public void noSuchElementFoundReturnsEmptyJsonObject() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnModelWithoutExampleData());

    // when/then
    assertThatThrownBy(() -> curriedReadExampleData.apply("task_missing"))
        .hasMessage(
            "BPMN Model [processId: '%s', elementId: 'task_missing'] does not contain element with id 'task_missing'",
            PROCESS_ID);
  }

  @Test
  public void malformedExampleDataReturnsTheMalformedData() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnModelWithMalformedExampleData());

    // when/then
    assertThat(curriedReadExampleData.apply("task_send_email")).isEqualTo("this is not valid json");
  }

  @Test
  public void emptyExampleDataThrowsException() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnModelWithEmptyExampleDataAttribute());

    // when/then
    assertThatThrownBy(() -> curriedReadExampleData.apply("service_task_review_email_c"))
        .hasMessage(
            "BPMN Model [processId: '%s', elementId: 'service_task_review_email_c'] has no example data as a property with the name '%s'.",
            PROCESS_ID, ZeebeConstants.PROPERTY_EXAMPLE_DATA);
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
