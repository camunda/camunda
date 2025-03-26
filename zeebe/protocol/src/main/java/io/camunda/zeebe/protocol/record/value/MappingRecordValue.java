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
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableMappingRecordValue.Builder.class)
public interface MappingRecordValue extends RecordValue {

  /** The unique identifier of the mapping within our system. */
  long getMappingKey();

  /** The name of the claim in the user token. */
  String getClaimName();

  /** The value of the claim in the user token. */
  String getClaimValue();

  /**
   * The descriptive name of the mapping. This field provides human-readable context about the
   * mapping.
   */
  String getName();

  /** The unique identifier of the mapping for external usage. */
  String getMappingId();
}
