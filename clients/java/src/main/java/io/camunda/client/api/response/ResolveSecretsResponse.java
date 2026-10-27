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

import io.camunda.client.api.search.enums.SecretErrorCode;
import java.util.List;
import java.util.Optional;

/**
 * The per-reference outcome of a resolve secrets command.
 *
 * <p>Each requested reference is authorized and resolved independently: a reference that could not
 * be resolved appears in {@link #getErrors()} and never fails the rest of the batch. Per-reference
 * failures are therefore response data, not exceptions.
 */
public interface ResolveSecretsResponse {

  /**
   * @return true if every requested reference was resolved, that is if there are no errors and
   *     every distinct requested reference appears in {@link #getResolved()}
   */
  boolean isFullyResolved();

  /**
   * @return the references that were resolved, never null, unmodifiable
   */
  List<ResolvedSecret> getResolved();

  /**
   * @return the references that could not be resolved, never null, unmodifiable
   */
  List<ResolutionError> getErrors();

  /**
   * Looks up a single resolved value by reference, so that a caller does not have to scan {@link
   * #getResolved()} itself. A map is deliberately not exposed instead, since {@code Map.toString()}
   * would print every value and undo the masking {@link ResolvedSecret} carries.
   *
   * @param reference the requested secret reference
   * @return the resolved value, or empty if the reference was not resolved
   */
  default Optional<String> getValue(final String reference) {
    return getResolved().stream()
        .filter(resolved -> resolved.getReference().equals(reference))
        .map(ResolvedSecret::getValue)
        .findFirst();
  }

  /** A reference that was resolved, together with its value. */
  interface ResolvedSecret {

    /**
     * @return the resolved secret reference
     */
    String getReference();

    /**
     * @return the resolved secret value
     */
    String getValue();
  }

  /** A reference that could not be resolved, together with its typed reason. */
  interface ResolutionError {

    /**
     * @return the secret reference that could not be resolved
     */
    String getReference();

    /**
     * @return the typed reason the reference could not be resolved
     */
    SecretErrorCode getCode();

    /**
     * The cluster documents this message as error metadata only, so it does not carry the secret
     * value. The client cannot enforce that, so treat it as server-provided text: it is left out of
     * {@code toString()} and reaches a log only where a caller puts it there.
     *
     * @return a human-readable description of the failure
     */
    String getMessage();
  }
}
