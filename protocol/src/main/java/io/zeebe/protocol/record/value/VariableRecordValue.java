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
package io.zeebe.protocol.record.value;

import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.intent.VariableIntent;

/**
 * Represents a variable related event.
 *
 * <p>See {@link VariableIntent} for intents.
 */
public interface VariableRecordValue extends RecordValue, ProcessInstanceRelated {

  /** @return the name of the variable. */
  String getName();

  /** @return the value of the variable as JSON string. */
  String getValue();

  /** @return the key of the scope the variable belongs to. */
  long getScopeKey();

  /** @return the key of the process instance the variable belongs to */
  long getProcessInstanceKey();

  /** @return the key of the process the variable belongs to */
  long getProcessDefinitionKey();
}
