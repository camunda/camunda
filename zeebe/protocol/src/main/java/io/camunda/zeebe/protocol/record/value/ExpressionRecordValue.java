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
import io.camunda.zeebe.protocol.record.RecordValueWithVariables;
import java.util.List;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/**
 * Represents a record value for a FEEL expression resolution in the engine.
 *
 * <p>A FEEL expression resolution record contains the expression to evaluate, optional variables,
 * and the result of the evaluation.
 *
 * <p>The value is immutable and built via {@link ImmutableExpressionRecordValue}.
 *
 * <ul>
 *   <li>{@link #getExpression()} – the FEEL expression to evaluate.
 *   <li>{@link #getVariables()} - the variables used as context for the expression evaluation.
 *   <li>{@link #getResultValue()} – the result value (nullable).
 *   <li>{@link #getWarnings()} – the list of warnings generated during evaluation.
 * </ul>
 *
 * @see RecordValue
 * @see TenantOwned
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableExpressionRecordValue.Builder.class)
public interface ExpressionRecordValue extends RecordValue, TenantOwned, RecordValueWithVariables {

  /**
   * Returns the FEEL expression to be evaluated.
   *
   * @return the FEEL expression as a String (never {@code null})
   */
  String getExpression();

  /**
   * Returns the result value of the expression evaluation.
   *
   * <p>The value can be nullable for certain result types like NUMBER, STRING, etc.
   *
   * @return the result value as a String (can be {@code null})
   */
  @Nullable Object getResultValue();

  /**
   * Returns a list of warnings generated during the expression evaluation.
   *
   * @return the list of warnings (never {@code null}, but can be empty)
   */
  List<String> getWarnings();

  /**
   * Returns the process or element instance key that provides context for this expression
   * evaluation.
   *
   * @return the scope key, or {@code -1} if not set
   */
  long getScopeKey();

  /**
   * Returns the secret references that were resolved from trusted sources while evaluating the
   * expression: a {@code camunda.secrets.<name>} reference used directly in the expression, or a
   * reference carried by a {@code SECRET_REFERENCE}-kind cluster variable the expression read.
   *
   * <p>References that merely appear in request-body variables or in plain (JSON-kind) cluster
   * variables are deliberately excluded — they are untrusted input and resolving them would
   * reintroduce a secret-injection vector. Callers (e.g. the connector runtime) use this list to
   * know which {@code camunda.secrets.<name>} occurrences in the result they may safely resolve.
   *
   * @return the referenced secrets (never {@code null}, but can be empty)
   */
  List<ExpressionSecretReferenceValue> getReferencedSecrets();

  /**
   * A secret reference resolved from a trusted source during expression evaluation. Carries the
   * reference identifier only, never the resolved secret value.
   */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableExpressionSecretReferenceValue.Builder.class)
  interface ExpressionSecretReferenceValue {

    /**
     * @return the identifier of the secret store that holds the referenced secret
     */
    String getStoreId();

    /**
     * @return the secret name, e.g. {@code token} for {@code camunda.secrets.token}; not the value
     */
    String getSecretReference();
  }
}
