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
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import java.util.Collections;
import java.util.Set;
import org.immutables.value.Value;

/**
 * Represents a variable related event.
 *
 * <p>See {@link VariableIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableVariableRecordValue.Builder.class)
public interface VariableRecordValue
    extends RecordValue, ProcessInstanceRelated, AuditLogProcessInstanceRelated, TenantOwned {

  /**
   * @return the name of the variable.
   */
  String getName();

  /**
   * @return the value of the variable as JSON string.
   */
  String getValue();

  /**
   * @return the key of the scope the variable belongs to.
   */
  long getScopeKey();

  /**
   * @return the key of the process instance the variable belongs to
   */
  @Override
  long getProcessInstanceKey();

  /**
   * @return the key of the process the variable belongs to
   */
  @Override
  long getProcessDefinitionKey();

  /**
   * @return the element instance key of the scope the variable belongs to.
   */
  @Override
  default long getElementInstanceKey() {
    return getScopeKey();
  }

  /**
   * Returns the key of the root process instance in the hierarchy. For variables in top-level
   * process instances, this is equal to {@link #getProcessInstanceKey()}. For variables in child
   * process instances (created via call activities), this is the key of the topmost parent process
   * instance.
   *
   * @return the key of the root process instance, or {@code -1} if not set
   */
  @Override
  long getRootProcessInstanceKey();

  /**
   * @return the BPMN process id this process instance belongs to.
   */
  @Override
  String getBpmnProcessId();

  /**
   * @return the source of the variable operation, e.g. {@code VariableOperationType.API} if the
   *     variable was triggered through the API, {@code VariableOperationType.USER_TASK_COMPLETION}
   *     if it was produced by native user task output processing, or {@code
   *     VariableOperationType.UNKNOWN} if the source is unknown.
   */
  VariableSourceValue getSource();

  /**
   * Declares which data-protection treatments apply to this variable's exported value (product-hub
   * #3805 and siblings), per the composition rule on {@link ProtectionMode} (redact is exclusive;
   * mask and encrypt compose freely). Computed once, when the variable is created or updated -- see
   * {@code io.camunda.zeebe.engine.processing.variable.VariableBehavior} -- and carried on the
   * record so every downstream consumer (redaction today; future masking/encryption) reads a
   * declared set instead of re-deriving it.
   *
   * @return the set of protection modes declared for this variable; empty if none.
   */
  @Value.Default
  default Set<ProtectionMode> getProtectionModes() {
    return Collections.emptySet();
  }

  /**
   * Convenience for callers that only care about redaction. Derived from {@link
   * #getProtectionModes()}, so implementations should annotate this override with
   * {@code @JsonIgnore} to avoid duplicating that field in JSON (this module has no Jackson
   * dependency, so the annotation cannot be applied here).
   *
   * @return {@code true} if {@link ProtectionMode#REDACT} is among the declared protection modes.
   */
  default boolean shouldBeRedacted() {
    return getProtectionModes().contains(ProtectionMode.REDACT);
  }
}
