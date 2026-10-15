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
import io.camunda.zeebe.protocol.record.RecordValue;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableProcessDefinitionRestoreRecordValue.Builder.class)
public interface ProcessDefinitionRestoreRecordValue extends RecordValue {

  /**
   * @return the key of the process definition this record refers to, or -1 if none (for example,
   *     the START/STARTED records that bound a restore run). A FINISHED record may retain the key
   *     of the process definition the final command resumed from.
   */
  long getProcessDefinitionKey();

  /**
   * @return the tenant id of the process definition this record refers to, or an empty string if
   *     none (for example, the START/STARTED records). A FINISHED record may retain the tenant id
   *     of the process definition the final command resumed from.
   */
  String getTenantId();

  /**
   * @return the number of process definitions restored so far by the current restore run on this
   *     partition, carried from one CONTINUE to the next; the FINISHED record holds the total
   */
  int getRestoredCount();
}
