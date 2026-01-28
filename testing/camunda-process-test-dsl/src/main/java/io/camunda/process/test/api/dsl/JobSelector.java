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

/** A selector to identify a job. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableJobSelector.Builder.class)
public interface JobSelector {

  /**
   * The type of the job.
   *
   * @return the job type or empty if not set
   */
  Optional<String> getJobType();

  /**
   * The ID of the BPMN element.
   *
   * @return the element ID or empty if not set
   */
  Optional<String> getElementId();

  /**
   * The process definition ID of the job.
   *
   * @return the process definition ID or empty if not set
   */
  Optional<String> getProcessDefinitionId();
}
