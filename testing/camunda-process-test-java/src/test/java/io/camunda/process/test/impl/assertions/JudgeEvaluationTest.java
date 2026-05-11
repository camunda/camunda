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
package io.camunda.process.test.impl.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import io.camunda.client.api.response.DocumentMetadata;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.api.judge.MultimodalChatModelAdapter;
import io.camunda.process.test.api.judge.ResolvedDocument;
import io.camunda.process.test.impl.judge.ResolvedDocumentImpl;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JudgeEvaluationTest {

  @Test
  void shouldParseMarkdownWrappedJsonResponse() {
    // given
    final String markdownResponse =
        "```json\n{\"score\": 0.85, \"reasoning\": \"Good match.\"}\n```";
    final JudgeEvaluation evaluation =
        new JudgeEvaluation(prompt -> markdownResponse, "some expectation", Optional.empty());

    // when
    final JudgeEvaluation.Result result = evaluation.evaluate("some value");

    // then
    assertThat(result.getScore()).isEqualTo(0.85);
    assertThat(result.getReasoning()).isEqualTo("Good match.");
  }

  @Test
  void shouldClampScoreAboveOneToOne() {
    // given
    final JudgeEvaluation evaluation =
        new JudgeEvaluation(
            prompt -> "{\"score\": 1.5, \"reasoning\": \"Over the top.\"}",
            "expectation",
            Optional.empty());

    // when
    final JudgeEvaluation.Result result = evaluation.evaluate("some value");

    // then
    assertThat(result.getScore()).isEqualTo(1.0);
  }

  @Test
  void shouldClampNegativeScoreToZero() {
    // given
    final JudgeEvaluation evaluation =
        new JudgeEvaluation(
            prompt -> "{\"score\": -0.3, \"reasoning\": \"Negative.\"}",
            "expectation",
            Optional.empty());

    // when
    final JudgeEvaluation.Result result = evaluation.evaluate("some value");

    // then
    assertThat(result.getScore()).isEqualTo(0.0);
  }

  @Test
  void shouldPropagateExceptionWhenLlmCallFails() {
    // given
    final JudgeEvaluation evaluation =
        new JudgeEvaluation(
            prompt -> {
              throw new RuntimeException("Connection refused");
            },
            "expectation",
            Optional.empty());

    // when / then
    assertThatThrownBy(() -> evaluation.evaluate("some value"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Connection refused");
  }

  @Test
  void shouldThrowParseExceptionWhenLlmReturnsEmptyResponse() {
    // given
    final JudgeEvaluation evaluation =
        new JudgeEvaluation(prompt -> "", "expectation", Optional.empty());

    // when / then
    assertThatThrownBy(() -> evaluation.evaluate("some value"))
        .isInstanceOf(JudgeResponseParseException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .cause()
        .hasMessageContaining("Empty response from judge");
  }

  @Test
  void shouldThrowParseExceptionWhenLlmReturnsNullResponse() {
    // given
    final JudgeEvaluation evaluation =
        new JudgeEvaluation(prompt -> null, "expectation", Optional.empty());

    // when / then
    assertThatThrownBy(() -> evaluation.evaluate("some value"))
        .isInstanceOf(JudgeResponseParseException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .cause()
        .hasMessageContaining("Empty response from judge");
  }

  @Test
  void shouldRoundScoreToTwoDecimalPlaces() {
    // given — 0.695 is the HALF_UP tie case: third decimal exactly 5 rounds up
    final JudgeEvaluation.Result result = new JudgeEvaluation.Result(0.695, "reasoning");

    // then
    assertThat(result.getScore()).isEqualTo(0.70);
  }

  @Test
  void shouldPassWhenScoreRoundsUpToThreshold() {
    // given — 0.695 rounds to 0.70, which meets the threshold
    final JudgeEvaluation.Result result = new JudgeEvaluation.Result(0.695, "reasoning");

    // then
    assertThat(result.passed(0.70)).isTrue();
  }

  @Test
  void shouldNotPassWhenScoreRoundsDownBelowThreshold() {
    // given — raw score 0.6940 rounds to 0.69, which is below the threshold
    final JudgeEvaluation.Result result = new JudgeEvaluation.Result(0.6940, "reasoning");

    // then
    assertThat(result.passed(0.70)).isFalse();
  }

  @Test
  void shouldPassWhenThresholdRoundsDownToScore() {
    // given — score 0.70, threshold 0.7049 rounds down to 0.70 → passes
    final JudgeEvaluation.Result result = new JudgeEvaluation.Result(0.70, "reasoning");

    // then
    assertThat(result.passed(0.7049)).isTrue();
  }

  @Test
  void shouldNotPassWhenThresholdRoundsUpAboveScore() {
    // given — score 0.70, threshold 0.705 rounds up to 0.71 → fails
    final JudgeEvaluation.Result result = new JudgeEvaluation.Result(0.70, "reasoning");

    // then
    assertThat(result.passed(0.705)).isFalse();
  }

  @Test
  void shouldRouteToMultimodalAdapterWhenDocumentsPresent() {
    // given
    final AtomicReference<String> capturedPrompt = new AtomicReference<>();
    final AtomicReference<List<ResolvedDocument>> capturedDocs = new AtomicReference<>();

    final MultimodalChatModelAdapter adapter =
        new TestMultimodalAdapter(
            (prompt, docs) -> {
              capturedPrompt.set(prompt);
              capturedDocs.set(docs);
              return "{\"score\": 1.0, \"reasoning\": \"ok\"}";
            });

    final JudgeEvaluation evaluation =
        new JudgeEvaluation(adapter, "expectation", Optional.empty());

    final ResolvedDocument doc =
        ResolvedDocumentImpl.resolved(
            refOf("doc-1", "image.png", "image/png"), new byte[] {1, 2, 3});

    // when
    final JudgeEvaluation.Result result =
        evaluation.evaluate("value", Collections.singletonList(doc));

    // then
    assertThat(result.getScore()).isEqualTo(1.0);
    assertThat(capturedDocs.get()).containsExactly(doc);
    assertThat(capturedPrompt.get()).contains("<resolved_documents>");
    assertThat(capturedPrompt.get()).contains("documentId=\"doc-1\"");
  }

  @Test
  void shouldIncludeUnresolvedDocumentMarkerInPrompt() {
    // given
    final AtomicReference<String> capturedPrompt = new AtomicReference<>();
    final MultimodalChatModelAdapter adapter =
        new TestMultimodalAdapter(
            (prompt, docs) -> {
              capturedPrompt.set(prompt);
              return "{\"score\": 0.5, \"reasoning\": \"ok\"}";
            });

    final JudgeEvaluation evaluation =
        new JudgeEvaluation(adapter, "expectation", Optional.empty());

    final ResolvedDocument failed =
        ResolvedDocumentImpl.failed(
            refOf("doc-3", "missing.pdf", "application/pdf"), "404 not found");

    // when
    evaluation.evaluate("value", Collections.singletonList(failed));

    // then
    assertThat(capturedPrompt.get()).contains("documentId=\"doc-3\"");
    assertThat(capturedPrompt.get()).contains("status=\"unresolved\"");
    assertThat(capturedPrompt.get()).contains("404 not found");
  }

  @Test
  void shouldNotAddDocumentsSectionWhenNoDocuments() {
    // given
    final AtomicReference<String> capturedPrompt = new AtomicReference<>();
    final ChatModelAdapter adapter =
        prompt -> {
          capturedPrompt.set(prompt);
          return "{\"score\": 1.0, \"reasoning\": \"ok\"}";
        };

    final JudgeEvaluation evaluation =
        new JudgeEvaluation(adapter, "expectation", Optional.empty());

    // when
    evaluation.evaluate("value");

    // then
    assertThat(capturedPrompt.get()).doesNotContain("<resolved_documents>");
  }

  private static DocumentReferenceResponse refOf(
      final String documentId, final String fileName, final String contentType) {
    final DocumentMetadata metadata = mock(DocumentMetadata.class);
    lenient().when(metadata.getFileName()).thenReturn(fileName);
    lenient().when(metadata.getContentType()).thenReturn(contentType);
    final DocumentReferenceResponse reference = mock(DocumentReferenceResponse.class);
    lenient().when(reference.getDocumentId()).thenReturn(documentId);
    lenient().when(reference.getMetadata()).thenReturn(metadata);
    return reference;
  }

  /** Test double that implements MultimodalChatModelAdapter. */
  private static final class TestMultimodalAdapter implements MultimodalChatModelAdapter {

    private final java.util.function.BiFunction<String, List<ResolvedDocument>, String> handler;

    TestMultimodalAdapter(
        final java.util.function.BiFunction<String, List<ResolvedDocument>, String> handler) {
      this.handler = handler;
    }

    @Override
    public String generate(final String prompt) {
      throw new AssertionError("string-only generate should not be called when documents present");
    }

    @Override
    public String generate(final String prompt, final List<ResolvedDocument> documents) {
      return handler.apply(prompt, documents);
    }
  }
}
