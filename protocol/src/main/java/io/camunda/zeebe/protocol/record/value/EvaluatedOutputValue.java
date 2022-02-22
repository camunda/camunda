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

import io.camunda.zeebe.protocol.record.RecordValue;

/**
 * An evaluated output of a decision table that belongs to a {@link MatchedRuleValue matched rule}.
 * It contains details of the output and the value of the evaluated output expression.
 */
public interface EvaluatedOutputValue extends RecordValue {

  /** @return the id of the evaluated output */
  String getOutputId();

  /** @return the name of the evaluated output */
  String getOutputName();

  /** @return the value of the evaluated output expression as JSON string */
  String getOutputValue();
}
