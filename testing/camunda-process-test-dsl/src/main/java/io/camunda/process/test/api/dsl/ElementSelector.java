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
package io.camunda.process.test.api.dsl;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Optional;
import org.immutables.value.Value;

/** A selector to identify a BPMN element. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableElementSelector.Builder.class)
public interface ElementSelector {

  /**
   * The ID of the BPMN element.
   *
   * @return the element id
   */
  Optional<String> getElementId();

  /**
   * The name of the BPMN element.
   *
   * @return the element name
   */
  Optional<String> getElementName();
}
