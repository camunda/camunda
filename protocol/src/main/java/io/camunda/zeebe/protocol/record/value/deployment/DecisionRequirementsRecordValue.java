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

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ZeebeImmutableProtocol;
import org.immutables.value.Value;

/**
 * Represents a deployed decision requirements graph (DRG/DRD) for a DMN resource. The decisions of
 * the DMN resource belongs to this DRG. The DMN resource itself is stored only in the DRG.
 */
@Value.Immutable
@ZeebeImmutableProtocol
public interface DecisionRequirementsRecordValue
    extends RecordValue, DecisionRequirementsMetadataValue {

  /** @return the binary DMN resource */
  byte[] getResource();
}
