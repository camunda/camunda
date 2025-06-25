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

public class Run {

  /** The id of the run */
  private final String id;

  /** The name of the run */
  private final String name;

  /** List of events that happened during the run */
  private final List<Event> events = new ArrayList<>();

  public Run(final String id, final String name) {
    this.id = id;
    this.name = name;
  }

  /** Adds an event to the run */
  public void addEvent(final Event event) {
    events.add(event);
  }

  /** Returns all events of the run */
  public Collection<Event> getEvents() {
    return events;
  }

  /** Returns all events for the given model key */
  public Collection<Event> getEvents(final String modelKey) {
    return events.stream()
        .filter(event -> event.getModelKey().equals(modelKey))
        .collect(Collectors.toList());
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
