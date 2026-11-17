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
package io.camunda.process.test.impl.assertions;

import io.camunda.client.api.search.enums.IncidentState;
import java.util.EnumSet;
import java.util.Set;

final class IncidentStateSupport {

  private static final Set<IncidentState> ACTIVE_STATES =
      EnumSet.of(IncidentState.ACTIVE, IncidentState.PENDING, IncidentState.MIGRATED);

  private IncidentStateSupport() {}

  static boolean isActive(final IncidentState state) {
    return ACTIVE_STATES.contains(state);
  }
}
