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
package io.camunda.zeebe.model.bpmn.validation.zeebe;

import static java.util.stream.Collectors.groupingBy;

import io.camunda.zeebe.model.bpmn.instance.Activity;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

/**
 * Indexes the boundary events of a scope by attached activity the first time one of its activities
 * is validated, instead of scanning the whole scope for every activity.
 *
 * <p>Stateful: the index is only valid while the model does not change, it retains the validated
 * model, and it is not thread-safe. Use a new instance for every validation walk.
 */
final class ScopeIndexedActivityValidator extends ActivityValidator {

  private final Map<ModelElementInstance, Map<Activity, List<BoundaryEvent>>>
      boundaryEventsByScope = new HashMap<>();

  @Override
  protected Collection<BoundaryEvent> boundaryEventsOf(final Activity activity) {
    return boundaryEventsByScope
        .computeIfAbsent(
            activity.getParentElement(),
            scope ->
                scope.getChildElementsByType(BoundaryEvent.class).stream()
                    .filter(event -> Objects.nonNull(event.getAttachedTo()))
                    .collect(groupingBy(BoundaryEvent::getAttachedTo)))
        .getOrDefault(activity, Collections.emptyList());
  }
}
