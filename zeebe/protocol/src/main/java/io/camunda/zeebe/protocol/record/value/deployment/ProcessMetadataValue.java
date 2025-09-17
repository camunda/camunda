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
package io.camunda.zeebe.protocol.record.value.deployment;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import org.immutables.value.Value;

/** Represents deployed process metadata, so all important properties of a deployed process. */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableProcessMetadataValue.Builder.class)
public interface ProcessMetadataValue extends ExtendedMetadataValue {
  /**
   * @return the bpmn process ID of this process
   */
  String getBpmnProcessId();

  /**
   * @return the key of this process
   */
  long getProcessDefinitionKey();

  @Override
  default String entityId() {
    return getBpmnProcessId();
  }

  @Override
  default long entityKey() {
    return getProcessDefinitionKey();
  }
}
