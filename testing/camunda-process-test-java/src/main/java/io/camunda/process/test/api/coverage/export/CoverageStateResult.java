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

import io.camunda.process.test.api.coverage.model.Coverage;
import io.camunda.process.test.api.coverage.model.Event;
import io.camunda.process.test.api.coverage.model.Model;
import io.camunda.process.test.api.coverage.model.Suite;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class CoverageStateResult implements Coverage {

  private final Collection<Suite> suites;
  private final Collection<Model> models;

  public CoverageStateResult(final Collection<Suite> suites, final Collection<Model> models) {
    this.suites = suites;
    this.models = models;
  }

  @Override
  public List<Event> getEvents() {
    final List<Event> events = new ArrayList<>();
    for (final Suite suite : suites) {
      events.addAll(suite.getEvents());
    }
    return events;
  }

  @Override
  public List<Event> getEvents(final String modelKey) {
    final List<Event> events = new ArrayList<>();
    for (final Suite suite : suites) {
      events.addAll(suite.getEvents(modelKey));
    }
    return events;
  }

  public Collection<Suite> getSuites() {
    return suites;
  }

  public Collection<Model> getModels() {
    return models;
  }

  @Override
  public int hashCode() {
    return Objects.hash(suites, models);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CoverageStateResult that = (CoverageStateResult) o;
    return Objects.equals(suites, that.suites) && Objects.equals(models, that.models);
  }
}
