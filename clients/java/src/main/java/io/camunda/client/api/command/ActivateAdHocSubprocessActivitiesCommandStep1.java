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
package io.camunda.client.api.command;

import io.camunda.client.api.response.ActivateAdHocSubprocessActivitiesResponse;
import java.util.Arrays;
import java.util.Collection;

public interface ActivateAdHocSubprocessActivitiesCommandStep1 {

  /**
   * Create an {@link io.camunda.client.protocol.rest.AdHocSubprocessActivateActivitiesInstruction}
   * for the given element id.
   *
   * @param elementId the id of the element to activate
   * @return the builder for this command
   */
  ActivateAdHocSubprocessActivitiesCommandStep2 activateElement(final String elementId);

  /**
   * Create an {@link io.camunda.client.protocol.rest.AdHocSubprocessActivateActivitiesInstruction}
   * for each of the given element ids.
   *
   * @param elementIds the ids of the elements to activate
   * @return the builder for this command
   * @throws IllegalArgumentException if elementIds is null or empty
   */
  default ActivateAdHocSubprocessActivitiesCommandStep2 activateElements(
      final Collection<String> elementIds) {
    if (elementIds == null || elementIds.isEmpty()) {
      throw new IllegalArgumentException("elementIds must not be empty");
    }

    ActivateAdHocSubprocessActivitiesCommandStep2 builder = null;
    for (final String elementId : elementIds) {
      builder = activateElement(elementId);
    }

    return builder;
  }

  /**
   * Create an {@link io.camunda.client.protocol.rest.AdHocSubprocessActivateActivitiesInstruction}
   * for each of the given element ids.
   *
   * @param elementIds the ids of the elements to activate
   * @return the builder for this command
   * @throws IllegalArgumentException if elementIds is null or empty
   */
  default ActivateAdHocSubprocessActivitiesCommandStep2 activateElements(
      final String... elementIds) {
    return activateElements(Arrays.asList(elementIds));
  }

  interface ActivateAdHocSubprocessActivitiesCommandStep2
      extends ActivateAdHocSubprocessActivitiesCommandStep1,
          FinalCommandStep<ActivateAdHocSubprocessActivitiesResponse> {}
}
