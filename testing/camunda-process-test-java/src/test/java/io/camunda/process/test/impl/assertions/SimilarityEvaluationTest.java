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
import static org.assertj.core.api.Assertions.within;

import io.camunda.process.test.api.similarity.TextPreprocessor;
import io.camunda.process.test.impl.similarity.preprocessors.LowercaseNormalizerPreprocessor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimilarityEvaluationTest {

  /** Returns a fixed embedding: the input text's UTF-16 char values as a float array. */
  private static float[] charEmbedding(final String text) {
    final float[] embedding = new float[text.length()];
    for (int i = 0; i < text.length(); i++) {
      embedding[i] = text.charAt(i);
    }
    return embedding;
  }

  @Test
  void shouldReturnScoreOneForIdenticalTexts() {
    // given
    final String text = "hello world";
    final SimilarityEvaluation evaluation =
        new SimilarityEvaluation(
            SimilarityEvaluationTest::charEmbedding, Collections.emptyList(), text);

    // when
    final SimilarityEvaluation.Result result = evaluation.evaluate(text);

    // then
    assertThat(result.getScore()).isCloseTo(1.0, within(1e-9));
  }

  @Test
  void shouldApplyPreprocessorsBeforeEmbedding() {
    // given — expected is mixed-case, actual is lower-case; after lowercasing they are equal
    final List<TextPreprocessor> preprocessors =
        Collections.singletonList(LowercaseNormalizerPreprocessor.INSTANCE);

    // We track what texts were passed to the embedding model
    final String[] embeddedTexts = new String[2];
    final int[] callCount = {0};

    final SimilarityEvaluation evaluation =
        new SimilarityEvaluation(
            text -> {
              embeddedTexts[callCount[0]++] = text;
              return charEmbedding(text);
            },
            preprocessors,
            "Hello World");

    // when
    evaluation.evaluate("hello world");

    // then — both texts should have been lowercased before embedding
    assertThat(embeddedTexts[0]).isEqualTo("hello world");
    assertThat(embeddedTexts[1]).isEqualTo("hello world");
  }

  @Test
  void shouldReturnCorrectScoreFromCosineSimilarity() {
    // given — use simple fixed embeddings with a known cosine similarity
    // [1,0] and [4,3] have cosine similarity = 4/5 = 0.8
    final SimilarityEvaluation evaluation =
        new SimilarityEvaluation(
            text -> text.equals("a") ? new float[] {1.0f, 0.0f} : new float[] {4.0f, 3.0f},
            Collections.emptyList(),
            "a");

    // when
    final SimilarityEvaluation.Result result = evaluation.evaluate("b");

    // then
    assertThat(result.getScore()).isCloseTo(0.8, within(1e-9));
  }

  @Test
  void shouldPassWhenScoreMeetsThreshold() {
    // given
    final String text = "identical";
    final SimilarityEvaluation evaluation =
        new SimilarityEvaluation(
            SimilarityEvaluationTest::charEmbedding, Collections.emptyList(), text);

    // when
    final SimilarityEvaluation.Result result = evaluation.evaluate(text);

    // then
    assertThat(result.passed(0.5)).isTrue();
    assertThat(result.passed(0.99)).isTrue();
  }

  @Test
  void shouldNotPassWhenScoreBelowThreshold() {
    // given — orthogonal embeddings give score 0.0
    final SimilarityEvaluation evaluation =
        new SimilarityEvaluation(
            text -> text.equals("a") ? new float[] {1.0f, 0.0f} : new float[] {0.0f, 1.0f},
            Collections.emptyList(),
            "a");

    // when
    final SimilarityEvaluation.Result result = evaluation.evaluate("b");

    // then
    assertThat(result.getScore()).isEqualTo(0.0);
    assertThat(result.passed(0.5)).isFalse();
    assertThat(result.passed(0.0)).isTrue();
  }

  @Test
  void shouldApplyMultiplePreprocessorsInOrder() {
    // given — preprocessors that append a suffix in order; verifies ordering
    final TextPreprocessor addA = text -> text + "A";
    final TextPreprocessor addB = text -> text + "B";
    final List<TextPreprocessor> preprocessors = Arrays.asList(addA, addB);

    final String[] embeddedTexts = new String[2];
    final int[] callCount = {0};

    final SimilarityEvaluation evaluation =
        new SimilarityEvaluation(
            text -> {
              embeddedTexts[callCount[0]++] = text;
              return new float[] {1.0f};
            },
            preprocessors,
            "x");

    // when
    evaluation.evaluate("y");

    // then — both inputs should have had "AB" appended in order
    assertThat(embeddedTexts[0]).isEqualTo("xAB");
    assertThat(embeddedTexts[1]).isEqualTo("yAB");
  }
}
