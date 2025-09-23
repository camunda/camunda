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
import io.camunda.process.test.impl.assertions.util.CamundaAssertJsonMapper;
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
public class BpmnExampleDataReaderImplTest {

  private static final long PROCESS_DEFINITION_KEY = 1L;

  private BpmnExampleDataReaderImpl reader;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient client;

  private final CamundaAssertJsonMapper jsonMapper =
      new CamundaAssertJsonMapper(new CamundaObjectMapper());

  @BeforeEach
  public void setup() {
    reader = new BpmnExampleDataReaderImpl(client, jsonMapper);
  }

  @Test
  public void successfulRead() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnModelWithExampleData());

    // when
    final Map<String, Object> variables =
        reader.readExampleData(PROCESS_DEFINITION_KEY, "task_send_email");

    // then
    final Map<String, Object> expected =
        Collections.singletonMap(
            "response", Collections.singletonMap("body", Collections.singletonMap("status", 200)));

    assertThat(variables).containsAllEntriesOf(expected);
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
    final Map<String, Object> variables =
        reader.readExampleData(PROCESS_DEFINITION_KEY, "task_send_email");

    // then
    final Map<String, Object> expected =
        Collections.singletonMap(
            "response", Collections.singletonMap("body", Collections.singletonMap("status", 200)));

    assertThat(variables).containsAllEntriesOf(expected);
  }

  @Test
  public void noExampleDataFound() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnModelWithoutExampleData());

    // when/then
    assertThatThrownBy(() -> reader.readExampleData(PROCESS_DEFINITION_KEY, "task_send_email"))
        .hasMessageContaining(
            "BPMN Model [process-definition-key: '1', element-id: 'task_send_email'] has no example data for the given element-id");
  }

  @Test
  public void malformedExampleData() {
    // given
    when(client.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnModelWithMalformedExampleData());

    // when/then
    assertThatThrownBy(() -> reader.readExampleData(PROCESS_DEFINITION_KEY, "task_send_email"))
        .hasMessageContaining(
            "BPMN Model [process-definition-key: '1', element-id: 'task_send_email'] failed to parse the example data 'this is not valid json'");
  }

  private String bpmnModelWithExampleData() {
    return bpmnModel("/mockJobWorker/send-email-with-example.bpmn");
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
