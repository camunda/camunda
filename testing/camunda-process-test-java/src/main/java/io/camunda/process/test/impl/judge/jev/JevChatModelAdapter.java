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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.process.test.api.judge.BatchExpectationChatModelAdapter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calls the Jev (TypeSafe) classifier API to evaluate several named yes/no ("Noul") questions
 * against one actual value in a single request.
 *
 * <p>This adapter does not support free-form prompting ({@link #generate(String)}): Jev's API is
 * shaped around typed classification questions, not text generation.
 *
 * <p>Prototype scope: only the Noul (yes/no truth value) primitive is used. Jev's Choice and Score
 * primitives are not wired up here, but would plug into a sibling method on {@link
 * BatchExpectationChatModelAdapter} or a new adapter interface following the same pattern.
 */
public final class JevChatModelAdapter implements BatchExpectationChatModelAdapter {

  static final String DEFAULT_BASE_URL = "https://api.typesafe.ai/v1/systemone";
  static final String DEFAULT_MODEL = "jev-latest";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final String model;
  private final JevTransport transport;

  public JevChatModelAdapter(final String apiKey) {
    this(apiKey, null, null);
  }

  public JevChatModelAdapter(final String apiKey, final String baseUrl, final String model) {
    this(
        model != null ? model : DEFAULT_MODEL,
        new Httpclient5JevTransport(baseUrl != null ? baseUrl : DEFAULT_BASE_URL, apiKey));
  }

  /** Package-private seam for tests: injects a {@link JevTransport} instead of real HTTP I/O. */
  JevChatModelAdapter(final String model, final JevTransport transport) {
    this.model = model;
    this.transport = transport;
  }

  @Override
  public String generate(final String prompt) {
    throw new UnsupportedOperationException(
        "JevChatModelAdapter only supports batched, typed expectation evaluation, not free-form "
            + "prompts. It is used automatically by satisfiesJudge/hasVariableSatisfiesJudge; "
            + "direct generate(String) calls are not supported.");
  }

  @Override
  public Map<String, Double> evaluateExpectations(
      final String actualValue, final Map<String, String> namedExpectations) {

    final String requestBody = buildRequest(actualValue, namedExpectations);
    final JevHttpResponse response = transport.post(requestBody);

    if (!response.isSuccessful()) {
      throw new JevRequestException(
          String.format(
              "Jev API call failed with status %d: %s",
              response.getStatusCode(), response.getBody()));
    }

    return parseAnswers(response.getBody(), namedExpectations.keySet());
  }

  private String buildRequest(
      final String actualValue, final Map<String, String> namedExpectations) {
    final ObjectNode root = OBJECT_MAPPER.createObjectNode();
    root.put("state", actualValue);
    root.put("model", model);

    final ObjectNode questions = root.putObject("questions");
    for (final Map.Entry<String, String> entry : namedExpectations.entrySet()) {
      final ObjectNode question = questions.putObject(entry.getKey());
      question.put("type", "noul");
      question.put("instructions", entry.getValue());
    }

    return root.toString();
  }

  private Map<String, Double> parseAnswers(
      final String responseBody, final Iterable<String> expectedKeys) {
    final JsonNode answers;
    try {
      answers = OBJECT_MAPPER.readTree(responseBody).path("answers");
    } catch (final Exception e) {
      throw new JevRequestException(
          "Failed to parse the Jev API response as JSON: " + responseBody, e);
    }

    final Map<String, Double> result = new LinkedHashMap<>();
    for (final String key : expectedKeys) {
      final JsonNode answer = answers.path(key);
      if (!answer.has("noul")) {
        throw new JevRequestException(
            String.format(
                "Jev API response is missing a 'noul' answer for question '%s'. Raw response: %s",
                key, responseBody));
      }
      result.put(key, answer.path("noul").asDouble());
    }
    return result;
  }
}
