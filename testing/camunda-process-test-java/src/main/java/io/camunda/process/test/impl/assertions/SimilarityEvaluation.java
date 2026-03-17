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

import io.camunda.process.test.api.similarity.EmbeddingModelAdapter;
import io.camunda.process.test.api.similarity.preprocessors.TextPreprocessor;
import java.util.List;

/** An evaluation that uses text embeddings to assess semantic similarity between two texts. */
class SimilarityEvaluation {

  private final EmbeddingModelAdapter embeddingModel;
  private final List<TextPreprocessor> preprocessors;
  private final String text1;
  private final String text2;

  public SimilarityEvaluation(
      final EmbeddingModelAdapter embeddingModel,
      final List<TextPreprocessor> preprocessors,
      final String text1,
      final String text2) {
    this.embeddingModel = embeddingModel;
    this.preprocessors = preprocessors;
    this.text1 = text1;
    this.text2 = text2;
  }

  public Result evaluate() {
    final String processedText1 = applyPreprocessors(text1);
    final String processedText2 = applyPreprocessors(text2);
    final float[] expectedEmbedding = embeddingModel.embed(processedText1);
    final float[] actualEmbedding = embeddingModel.embed(processedText2);
    final double score = CosineSimilarity.compute(expectedEmbedding, actualEmbedding);
    return new Result(score);
  }

  private String applyPreprocessors(final String text) {
    String result = text;
    for (final TextPreprocessor preprocessor : preprocessors) {
      result = preprocessor.process(result);
    }
    return result;
  }

  /** The result of a semantic similarity evaluation, containing a score. */
  static class Result {

    private final double score;

    public Result(final double score) {
      this.score = score;
    }

    /**
     * Returns the similarity score between 0.0 and 1.0.
     *
     * @return the score
     */
    public double getScore() {
      return score;
    }

    /**
     * Returns whether the evaluation passed the given threshold.
     *
     * @param threshold the threshold score (0-1)
     * @return true if the score is greater than or equal to the threshold
     */
    public boolean passed(final double threshold) {
      return score >= threshold;
    }
  }
}
