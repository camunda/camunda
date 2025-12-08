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
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableExpressionRecordValue.Builder.class)
public interface ExpressionRecordValue extends RecordValue, TenantOwned {

  String getExpression();

  Map<String, Object> getContext();

  ExpressionScopeType getScopeType();

  long getProcessInstanceKey();

  Map<String, Object> getResult();

  String getResultType();

  List<EvaluationWarning> getWarnings();

  String getRejectionReason();

  /** Represents a warning that occurred during FEEL expression evaluation. */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableEvaluationWarning.Builder.class)
  interface EvaluationWarning {
    /**
     * @return the type or category of the warning
     */
    String getType();

    /**
     * @return a description of the warning
     */
    String getMessage();
  }
}
