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
 * Represents an instruction to control in a given ad-hoc sub-process.
 *
 * <p>See {@link AdHocSubProcessInstructionIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableAdHocSubProcessInstructionRecordValue.Builder.class)
public interface AdHocSubProcessInstructionRecordValue extends RecordValue, TenantOwned {

  /**
   * @return the instance key of the ad-hoc sub-process to control.
   */
  String getAdHocSubProcessInstanceKey();

  /**
   * @return the list of element ids of the activities to activate.
   */
  List<AdHocSubProcessInstructionElementValue> getElements();

  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableAdHocSubProcessInstructionElementValue.Builder.class)
  interface AdHocSubProcessInstructionElementValue {
    String getElementId();

    Map<String, Object> getVariables();
  }
}
