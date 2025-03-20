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
import java.util.List;
import org.immutables.value.Value;

/**
 * Represents a command to activate activities in a given ad-hoc subprocess.
 *
 * <p>See {@link io.camunda.zeebe.protocol.record.intent.AdHocSubProcessActivityActivationIntent}
 * for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableAdHocSubProcessActivityActivationRecordValue.Builder.class)
public interface AdHocSubProcessActivityActivationRecordValue extends RecordValue, TenantOwned {

  /**
   * @return the instance key of the ad-hoc subprocess that will have its activities activated.
   */
  String getAdHocSubProcessInstanceKey();

  /**
   * @return the list of flow node ids of the activities that need to be activated.
   */
  List<AdHocSubProcessActivityActivationElementValue> getElements();

  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableAdHocSubProcessActivityActivationElementValue.Builder.class)
  interface AdHocSubProcessActivityActivationElementValue {
    String getElementId();
  }
}
