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
package io.camunda.process.test.impl.coverage.report;

import io.camunda.process.test.impl.coverage.model.Coverage;
import java.util.Collection;

public class AggregatedSuiteInfo {
  private final String id;
  private final String name;
  private final Collection<Coverage> coverages;

  public AggregatedSuiteInfo(
      final String id, final String name, final Collection<Coverage> coverages) {
    this.id = id;
    this.name = name;
    this.coverages = coverages;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Collection<Coverage> getCoverages() {
    return coverages;
  }
}
