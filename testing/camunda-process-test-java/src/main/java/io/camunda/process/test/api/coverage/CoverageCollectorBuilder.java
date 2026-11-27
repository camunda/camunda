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
package io.camunda.process.test.api.coverage;

import java.util.List;
import java.util.function.Consumer;

/** A builder to configure and create a {@link CoverageCollector}. */
public interface CoverageCollectorBuilder {

  /**
   * Sets the process definition ids to exclude from the coverage.
   *
   * @param processDefinitionIds the process definition ids to exclude
   * @return this builder
   */
  CoverageCollectorBuilder excludeProcessDefinitionIds(List<String> processDefinitionIds);

  /**
   * Sets the decision definition ids to exclude from the coverage.
   *
   * @param decisionDefinitionIds the decision definition ids to exclude
   * @return this builder
   */
  CoverageCollectorBuilder excludeDecisionDefinitionIds(List<String> decisionDefinitionIds);

  /**
   * Sets the directory to write the coverage report to. By default, {@code
   * target/process-test-coverage/}.
   *
   * @param reportDirectory the path of the directory
   * @return this builder
   */
  CoverageCollectorBuilder reportDirectory(String reportDirectory);

  /**
   * Sets the consumer of the coverage summary. By default, the summary is logged.
   *
   * @param printStream the consumer to handle the summary
   * @return this builder
   */
  CoverageCollectorBuilder printStream(Consumer<String> printStream);

  /**
   * Sets the maximum number of results per query when the collector reads the data from the
   * cluster. By default, the configured query page limit of the process test runtime.
   *
   * @param queryPageLimit the maximum number of results per query
   * @return this builder
   */
  CoverageCollectorBuilder queryPageLimit(int queryPageLimit);

  /**
   * Creates the collector with the configuration of this builder.
   *
   * @return a new collector
   */
  CoverageCollector build();
}
