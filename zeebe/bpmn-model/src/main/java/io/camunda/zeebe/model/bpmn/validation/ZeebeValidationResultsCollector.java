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
package io.camunda.zeebe.model.bpmn.validation;

import static java.util.stream.Collectors.groupingBy;

import io.camunda.zeebe.model.bpmn.instance.Activity;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.camunda.bpm.model.xml.impl.validation.ValidationResultsCollectorImpl;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

/**
 * Collects the validation results of a single walk over a model, and caches lookups which would
 * otherwise be repeated for every element during that walk.
 *
 * <p>The cache is only valid as long as the model does not change, so a new collector must be used
 * for every validation.
 */
public final class ZeebeValidationResultsCollector extends ValidationResultsCollectorImpl {

  private final Map<ModelElementInstance, Map<Activity, List<BoundaryEvent>>>
      boundaryEventsByParent = new HashMap<>();

  /**
   * Returns the boundary events attached to the given activity, like {@link
   * Activity#getBoundaryEvents()}. The boundary events of a scope are looked up only once, instead
   * of scanning all elements of the scope for every activity in it.
   */
  public Collection<BoundaryEvent> boundaryEventsOf(final Activity activity) {
    return boundaryEventsByParent
        .computeIfAbsent(
            activity.getParentElement(),
            parent ->
                parent.getChildElementsByType(BoundaryEvent.class).stream()
                    .filter(event -> Objects.nonNull(event.getAttachedTo()))
                    .collect(groupingBy(BoundaryEvent::getAttachedTo)))
        .getOrDefault(activity, Collections.emptyList());
  }
}
