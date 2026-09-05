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
package io.camunda.client.api.response;

/**
 * A {@code camunda.secrets.<name>} reference that was resolved from a trusted source while
 * evaluating an expression: a reference used directly in the expression, or one carried by a {@code
 * SECRET_REFERENCE}-kind cluster variable the expression read. Callers use this to know which
 * references in the result they may safely resolve.
 */
public interface SecretReference {

  /**
   * Returns the identifier of the secret store that holds the referenced secret.
   *
   * @return the secret store identifier, e.g. {@code "default"}
   */
  String getStoreId();

  /**
   * Returns the secret name.
   *
   * @return the secret name, e.g. {@code "token"} for {@code "camunda.secrets.token"}
   */
  String getSecretName();
}
