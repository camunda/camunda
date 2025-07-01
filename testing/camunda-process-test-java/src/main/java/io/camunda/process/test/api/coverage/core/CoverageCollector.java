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
package io.camunda.process.test.api.coverage.core;

import io.camunda.process.test.api.coverage.model.Event;
import io.camunda.process.test.api.coverage.model.Model;
import io.camunda.process.test.api.coverage.model.Run;
import io.camunda.process.test.api.coverage.model.Suite;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.util.*;

/** Collector for the coverage. */
public class CoverageCollector {

  private final List<String> excludedProcessDefinitionKeys = new ArrayList<>();
  private final Map<String, Suite> suites = new HashMap<>();
  private final Map<String, Model> models = new HashMap<>();

  private Run activeRun;
  private Suite activeSuite;

  public void addSuite(final String suiteId, final String name) {
    final Suite suite = new Suite(suiteId, name);
    if (suites.containsKey(suite.getId())) {
      throw new IllegalArgumentException("Suite already exists");
    }
    suites.put(suite.getId(), suite);
  }

  public void addRun(final String runId, final String name) {
    final String suiteId = getActiveSuite().getId();
    final Run run = new Run(runId, name);
    getSuite(suiteId).addRun(run);
  }

  public void activateSuite(final String suiteId) {
    activeSuite = getSuite(suiteId);
  }

  public Suite getActiveSuite() {
    return activeSuite;
  }

  public void activateRun(final String runId) {
    if (activeSuite == null) {
      throw new IllegalStateException("No active suite available");
    }
    activeRun = activeSuite.getRun(runId);
    if (activeRun == null) {
      throw new IllegalArgumentException(
          "Run " + runId + " doesn't exist in suite " + activeSuite.getId());
    }
  }

  public void addEvent(final Event event, final CamundaDataSource dataSource) {
    if (activeRun == null) {
      throw new IllegalStateException("No active run available");
    }

    if (excludedProcessDefinitionKeys.contains(event.getModelKey())) {
      return;
    }
    addModelIfMissing(event, dataSource);
    activeRun.addEvent(event);
  }

  public void setExcludedProcessDefinitionKeys(final List<String> excludedProcessDefinitionKeys) {
    this.excludedProcessDefinitionKeys.clear();
    this.excludedProcessDefinitionKeys.addAll(excludedProcessDefinitionKeys);
  }

  public Collection<Model> getModels() {
    return models.values();
  }

  public Map<String, Suite> getSuites() {
    return suites;
  }

  private Suite getSuite(final String suiteId) {
    if (!suites.containsKey(suiteId)) {
      throw new IllegalArgumentException("Suite with id " + suiteId + " doesn't exist");
    }
    return suites.get(suiteId);
  }

  private void addModelIfMissing(final Event event, final CamundaDataSource dataSource) {
    if (models.containsKey(event.getModelKey())) {
      return;
    }
    final ModelProvider modelProvider = new CamundaModelProvider(dataSource);
    final Model model = modelProvider.getModel(event.getModelKey());
    models.put(model.getKey(), model);
  }
}
