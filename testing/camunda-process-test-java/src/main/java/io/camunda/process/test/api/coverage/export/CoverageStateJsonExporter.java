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
package io.camunda.process.test.api.coverage.export;

import camundajar.impl.com.google.gson.Gson;
import io.camunda.process.test.api.coverage.model.Model;
import io.camunda.process.test.api.coverage.model.Suite;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Exporter for Coverage State
 *
 * @author dominikhorn
 */
public class CoverageStateJsonExporter {

  private CoverageStateJsonExporter() {
    // Utility class, prevent instantiation
  }

  /**
   * Creates a Json String from the given input.
   *
   * @param suites Suites that should be exported
   * @param models Models that should be exported
   * @return XML String representation.
   */
  public static String createCoverageStateResult(
      final Collection<Suite> suites, final Collection<Model> models) {
    return new Gson().toJson(new CoverageStateResult(suites, models));
  }

  /**
   * Creates a Json String from the given input with empty collections as default values.
   *
   * @return XML String representation.
   */
  public static String createCoverageStateResult() {
    return createCoverageStateResult(Collections.emptyList(), Collections.emptyList());
  }

  public static CoverageStateResult readCoverageStateResult(final String json) {
    return new Gson().fromJson(json, CoverageStateResult.class);
  }

  public static String combineCoverageStateResults(final String json1, final String json2) {
    final CoverageStateResult result1 = readCoverageStateResult(json1);
    final CoverageStateResult result2 = readCoverageStateResult(json2);

    // Combine suites
    final List<Suite> combinedSuites = new ArrayList<>(result1.getSuites());
    combinedSuites.addAll(result2.getSuites());

    // Get keys of models in result1
    final List<String> existingModelKeys =
        result1.getModels().stream().map(Model::getKey).collect(Collectors.toList());

    // Filter models from result2 that aren't in result1
    final List<Model> newModels =
        result2.getModels().stream()
            .filter(model -> !existingModelKeys.contains(model.getKey()))
            .collect(Collectors.toList());

    // Combine models
    final List<Model> combinedModels = new ArrayList<>(result1.getModels());
    combinedModels.addAll(newModels);

    return createCoverageStateResult(combinedSuites, combinedModels);
  }
}
