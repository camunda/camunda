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
import io.camunda.process.test.api.similarity.TextPreprocessor;
import java.util.List;

/** An evaluation that uses text embeddings to assess semantic similarity between two texts. */
public class SimilarityEvaluation {

  private final EmbeddingModelAdapter embeddingModel;
  private final List<TextPreprocessor> preprocessors;
  private final float[] expectedEmbedding;

  public SimilarityEvaluation(
      final EmbeddingModelAdapter embeddingModel,
      final List<TextPreprocessor> preprocessors,
      final String expectation) {
    this.embeddingModel = embeddingModel;
    this.preprocessors = preprocessors;
    expectedEmbedding = embeddingModel.embed(applyPreprocessors(expectation));
  }

  public Result evaluate(final String input) {
    final String processedInput = applyPreprocessors(input);
    final float[] inputEmbedding = embeddingModel.embed(processedInput);
    final double score = CosineSimilarity.compute(expectedEmbedding, inputEmbedding);
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
  public static class Result {

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
