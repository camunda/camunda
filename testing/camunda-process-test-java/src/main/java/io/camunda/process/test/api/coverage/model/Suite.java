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
package io.camunda.process.test.api.coverage.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** A suite includes several Runs and contains the data for the coverage calculation. */
public class Suite implements Coverage {

  /** The id of the suite */
  private final String id;

  /** The name of the suite */
  private final String name;

  /** List of runs that are in */
  private final List<Run> runs = new ArrayList<>();

  public Suite(final String id, final String name) {
    this.id = id;
    this.name = name;
  }

  /** Adds a Run to the suite */
  public void addRun(final Run run) {
    runs.add(run);
  }

  /** Returns all events of the suite */
  @Override
  public Collection<Event> getEvents() {
    return runs.stream().flatMap(run -> run.getEvents().stream()).collect(Collectors.toList());
  }

  /** Returns all events for the given model key */
  @Override
  public Collection<Event> getEvents(final String modelKey) {
    return runs.stream()
        .flatMap(run -> run.getEvents(modelKey).stream())
        .collect(Collectors.toList());
  }

  /** Retrieves a Run by its ID */
  public Run getRun(final String runId) {
    return runs.stream().filter(run -> run.getId().equals(runId)).findFirst().orElse(null);
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
