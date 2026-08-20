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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * Process model metadata used by the coverage report.
 *
 * <p>Contains definition metadata and BPMN XML required by the coverage viewer.
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableProcessModel.Builder.class)
public interface ProcessModel {
  /** Returns the process definition id. */
  String getProcessDefinitionId();

  /**
   * Returns the human-readable process name, or {@code null} if no name is defined.
   *
   * <p>This is the {@code name} attribute of the BPMN {@code <process>} element.
   */
  @Nullable
  String getProcessName();

  /** Returns the number of coverable BPMN elements in this model. */
  int getTotalElementCount();

  /** Returns the process definition version. */
  String getVersion();

  /** Returns the BPMN XML content of this model. */
  String getXml();
}
