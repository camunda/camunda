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
import io.camunda.zeebe.protocol.record.RecordValue;
import java.util.Set;
import org.immutables.value.Value;

/**
 * Metadata that describes how a single variable is to be treated, e.g. whether it is protected
 * before it is exported. New metadata is added as additional properties, each with a default.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableVariableMetadataValue.Builder.class)
public interface VariableMetadataValue extends RecordValue {

  /**
   * @return the protections applied to the variable value before it is exported, empty if the
   *     variable is not protected.
   */
  Set<ProtectionMode> getProtectionModes();
}
