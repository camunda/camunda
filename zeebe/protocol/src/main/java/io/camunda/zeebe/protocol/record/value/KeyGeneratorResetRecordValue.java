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

/**
 * Represents a command to reset the key generator for a partition.
 *
 * <p>This is an administrative operation that allows setting a new starting point for key
 * generation within a partition. The key generator will only accept new values that are higher than
 * the current key.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableKeyGeneratorResetRecordValue.Builder.class)
public interface KeyGeneratorResetRecordValue extends RecordValue {

  /**
   * @return the partition ID for which the key generator should be reset
   */
  int getPartitionId();

  /**
   * @return the new key value to set. Must be properly encoded with the partition ID.
   */
  long getNewKeyValue();
}
