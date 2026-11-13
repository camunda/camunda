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
import io.camunda.zeebe.protocol.record.intent.CaseIntent;
import java.util.List;
import org.immutables.value.Value;

/**
 * A case: a root process instance of a process marked with {@code zeebe:case}, shown and searched
 * as a business case.
 *
 * <p>See {@link CaseIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableCaseRecordValue.Builder.class)
public interface CaseRecordValue extends RecordValue, ProcessInstanceRelated, TenantOwned {

  /**
   * @return the key of the root process instance this case belongs to, which is also the key of the
   *     case
   */
  long getProcessInstanceKey();

  /**
   * @return the BPMN process id of the process definition of the root process instance
   */
  String getBpmnProcessId();

  /**
   * @return the business id of the root process instance, or an empty string if it has none
   */
  String getBusinessId();

  /**
   * @return the title of the case, or an empty string if none
   */
  String getTitle();

  /**
   * Returns the stages of the case. A {@link CaseIntent#CREATED} event carries all stages of the
   * case; a {@link CaseIntent#STAGE_UPDATED} event carries only the changed stage with its new
   * status.
   *
   * @return the stages, or an empty list if none
   */
  List<CaseStageValue> getStages();

  /** A stage of a case. */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableCaseStageValue.Builder.class)
  interface CaseStageValue {

    /**
     * @return the id of the BPMN element that models the stage
     */
    String getElementId();

    /**
     * @return the status of the stage
     */
    CaseStageStatus getStatus();
  }
}
