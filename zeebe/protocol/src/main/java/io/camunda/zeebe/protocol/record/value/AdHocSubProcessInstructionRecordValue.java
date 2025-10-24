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
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

/**
 * Represents a command to modify a given ad-hoc sub-process.
 *
 * <p>See {@link AdHocSubProcessInstructionIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableAdHocSubProcessInstructionRecordValue.Builder.class)
public interface AdHocSubProcessInstructionRecordValue extends RecordValue, TenantOwned {

  /**
   * @return the instance key of the ad-hoc sub-process to modify.
   */
  long getAdHocSubProcessInstanceKey();

  /**
   * @return the list of elements that should be activated.
   */
  List<AdHocSubProcessActivateElementInstructionValue> getActivateElements();

  boolean isCancelRemainingInstances();

  boolean isCompletionConditionFulfilled();

  @Value.Immutable
  @ImmutableProtocol(
      builder = ImmutableAdHocSubProcessActivateElementInstructionValue.Builder.class)
  interface AdHocSubProcessActivateElementInstructionValue {
    String getElementId();

    /** Returns the variables of this instruction. Can be empty. */
    Map<String, Object> getVariables();
  }
}
