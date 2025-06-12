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

import java.util.*;
import java.util.stream.Collectors;

public interface Coverage {

  /** Retrieve all collected events. */
  Collection<Event> getEvents();

  /**
   * Retrieve events collected for a particular model.
   *
   * @param modelKey process model definition key.
   * @return list of events collected for a provided model.
   */
  Collection<Event> getEvents(String modelKey);

  /**
   * Returns all events for the given model key distinct by definitionKey.
   *
   * @param modelKey The key of the model.
   * @return events
   */
  default Collection<Event> getEventsDistinct(final String modelKey) {
    // FIXME: WHAT DOES IT SUPPOSE TO DO?
    final Map<String, Event> eventMap = new HashMap<>();
    getEvents(modelKey)
        .forEach(
            event -> {
              if (!eventMap.containsKey(event.getDefinitionKey())) {
                eventMap.put(event.getDefinitionKey(), event);
              }
            });
    return eventMap.values();
  }

  /**
   * Calculates the coverage for the given model.
   *
   * @param model the model
   * @return coverage
   */
  default double calculateCoverage(final Model model) {
    return (double) getEventsDistinct(model.getKey()).size() / model.getTotalElementCount();
  }

  /**
   * Calculates the coverage for the given models.
   *
   * @param models the models
   * @return coverage
   */
  default double calculateCoverage(final Collection<Model> models) {
    // Todo what about elements that are only started
    final List<Model> filteredModels =
        models.stream()
            .filter(
                model ->
                    getEvents().stream().anyMatch(evt -> evt.getModelKey().equals(model.getKey())))
            .collect(Collectors.toList());

    final int totalElementCount =
        filteredModels.stream().mapToInt(Model::getTotalElementCount).sum();

    final int coveredElementCount =
        filteredModels.stream().mapToInt(model -> getEventsDistinct(model.getKey()).size()).sum();

    return (double) coveredElementCount / totalElementCount;
  }
}
