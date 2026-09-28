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
package io.camunda.process.test.impl.judge.jev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JevChatModelAdapterTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void shouldSendAllNamedQuestionsInOneRequestAndParseTruthValues() throws Exception {
    // given
    final RecordingJevTransport transport =
        new RecordingJevTransport(
            new JevHttpResponse(
                200,
                "{\"answers\": {"
                    + "\"criterion_0\": {\"type\": \"noul\", \"noul\": 0.93}, "
                    + "\"criterion_1\": {\"type\": \"noul\", \"noul\": 0.41}}}"));
    final JevChatModelAdapter adapter = new JevChatModelAdapter("jev-latest", transport);

    final Map<String, String> namedExpectations = new LinkedHashMap<>();
    namedExpectations.put("criterion_0", "should be a polite greeting");
    namedExpectations.put("criterion_1", "should mention a refund");

    // when
    final Map<String, Double> truthValues =
        adapter.evaluateExpectations("Hello! Here is your refund.", namedExpectations);

    // then
    assertThat(truthValues).containsEntry("criterion_0", 0.93).containsEntry("criterion_1", 0.41);

    final JsonNode request = OBJECT_MAPPER.readTree(transport.lastRequestBody);
    assertThat(request.path("state").asText()).isEqualTo("Hello! Here is your refund.");
    assertThat(request.path("model").asText()).isEqualTo("jev-latest");
    assertThat(request.path("questions").path("criterion_0").path("type").asText())
        .isEqualTo("noul");
    assertThat(request.path("questions").path("criterion_0").path("instructions").asText())
        .isEqualTo("should be a polite greeting");
    assertThat(request.path("questions").path("criterion_1").path("instructions").asText())
        .isEqualTo("should mention a refund");
  }

  @Test
  void shouldThrowWhenHttpCallFails() {
    // given
    final JevChatModelAdapter adapter =
        new JevChatModelAdapter(
            "jev-latest", new RecordingJevTransport(new JevHttpResponse(500, "internal error")));

    // when / then
    assertThatThrownBy(
            () ->
                adapter.evaluateExpectations(
                    "some value", java.util.Collections.singletonMap("c0", "some criterion")))
        .isInstanceOf(JevRequestException.class)
        .hasMessageContaining("500")
        .hasMessageContaining("internal error");
  }

  @Test
  void shouldThrowWhenResponseIsMissingAnAnswer() {
    // given
    final JevChatModelAdapter adapter =
        new JevChatModelAdapter(
            "jev-latest", new RecordingJevTransport(new JevHttpResponse(200, "{\"answers\": {}}")));

    // when / then
    assertThatThrownBy(
            () ->
                adapter.evaluateExpectations(
                    "some value", java.util.Collections.singletonMap("c0", "some criterion")))
        .isInstanceOf(JevRequestException.class)
        .hasMessageContaining("c0");
  }

  @Test
  void shouldThrowWhenResponseIsNotValidJson() {
    // given
    final JevChatModelAdapter adapter =
        new JevChatModelAdapter(
            "jev-latest", new RecordingJevTransport(new JevHttpResponse(200, "not json")));

    // when / then
    assertThatThrownBy(
            () ->
                adapter.evaluateExpectations(
                    "some value", java.util.Collections.singletonMap("c0", "some criterion")))
        .isInstanceOf(JevRequestException.class);
  }

  @Test
  void generateShouldThrowUnsupportedOperationException() {
    // given
    final JevChatModelAdapter adapter =
        new JevChatModelAdapter(
            "jev-latest", new RecordingJevTransport(new JevHttpResponse(200, "{}")));

    // when / then
    assertThatThrownBy(() -> adapter.generate("some prompt"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  /** Test double capturing the last request body instead of performing real HTTP I/O. */
  private static final class RecordingJevTransport implements JevTransport {

    private final JevHttpResponse response;
    private String lastRequestBody;

    private RecordingJevTransport(final JevHttpResponse response) {
      this.response = response;
    }

    @Override
    public JevHttpResponse post(final String requestBody) {
      lastRequestBody = requestBody;
      return response;
    }
  }
}
