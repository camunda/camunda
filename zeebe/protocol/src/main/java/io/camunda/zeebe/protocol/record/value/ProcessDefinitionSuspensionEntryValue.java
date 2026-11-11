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
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import org.immutables.value.Value;

/** A process definition version that a suspension or resumption resolved to, and its outcome. */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableProcessDefinitionSuspensionEntryValue.Builder.class)
public interface ProcessDefinitionSuspensionEntryValue {

  /** The key of the process definition version. */
  long getProcessDefinitionKey();

  /** The version of the process definition. */
  int getVersion();

  /** What the suspension or resumption did to this version. */
  ProcessDefinitionSuspensionOutcome getOutcome();
}
