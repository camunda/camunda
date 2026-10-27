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
package io.camunda.client.impl.response;

import io.camunda.client.api.response.ResolveSecretsResponse;
import io.camunda.client.api.search.enums.SecretErrorCode;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.SecretResolveResult;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Copies the response eagerly instead of holding the wire representation, so that the resolved
 * values are only reachable through the getters below and cannot be printed by a generated {@code
 * toString()}.
 */
public class ResolveSecretsResponseImpl implements ResolveSecretsResponse {

  private final List<ResolvedSecret> resolved;
  private final List<ResolutionError> errors;
  private final boolean fullyResolved;

  public ResolveSecretsResponseImpl(
      final SecretResolveResult result, final Set<String> requestedReferences) {
    resolved =
        CollectionUtil.mapToUnmodifiableList(
            result.getResolved(),
            item -> new ResolvedSecretImpl(item.getReference(), item.getValue()));
    errors =
        CollectionUtil.mapToUnmodifiableList(
            result.getErrors(),
            item ->
                new ResolutionErrorImpl(
                    item.getReference(), errorCode(item.getCode()), item.getMessage()));
    fullyResolved = errors.isEmpty() && resolvedReferences().containsAll(requestedReferences);
  }

  /**
   * An absent or unrecognized code is reported as {@link SecretErrorCode#UNKNOWN_ENUM_VALUE} rather
   * than thrown, so that a spec addition the client does not know about yet is reported per
   * reference instead of failing the whole batch with a {@code RuntimeException}.
   */
  private static SecretErrorCode errorCode(
      final io.camunda.client.protocol.rest.SecretErrorCode code) {
    if (code == null) {
      return SecretErrorCode.UNKNOWN_ENUM_VALUE;
    }
    switch (code) {
      case NOT_FOUND:
        return SecretErrorCode.NOT_FOUND;
      case ACCESS_DENIED:
        return SecretErrorCode.ACCESS_DENIED;
      case INVALID_REFERENCE:
        return SecretErrorCode.INVALID_REFERENCE;
      case UNREADABLE:
        return SecretErrorCode.UNREADABLE;
      case UNKNOWN_DEFAULT_OPEN_API:
      default:
        EnumUtil.logUnknownEnumValue(code, "secret error code", SecretErrorCode.values());
        return SecretErrorCode.UNKNOWN_ENUM_VALUE;
    }
  }

  private Set<String> resolvedReferences() {
    return resolved.stream().map(ResolvedSecret::getReference).collect(Collectors.toSet());
  }

  /**
   * Requires every requested reference to be present in {@link #getResolved()}, not merely that no
   * error was reported: a response that dropped a reference has no errors either, and calling that
   * fully resolved would send a caller to a resolved entry that is not there. Matching the
   * references themselves rather than counting them also holds when the response repeats one
   * reference and omits another.
   */
  @Override
  public boolean isFullyResolved() {
    return fullyResolved;
  }

  @Override
  public List<ResolvedSecret> getResolved() {
    return resolved;
  }

  @Override
  public List<ResolutionError> getErrors() {
    return errors;
  }

  /**
   * Prints the resolved references but never their values, so that logging cannot leak a secret.
   */
  @Override
  public String toString() {
    return "ResolveSecretsResponse{resolved=" + resolved + ", errors=" + errors + '}';
  }

  public static final class ResolvedSecretImpl implements ResolvedSecret {

    private final String reference;
    private final String value;

    public ResolvedSecretImpl(final String reference, final String value) {
      this.reference = reference;
      this.value = value;
    }

    @Override
    public String getReference() {
      return reference;
    }

    @Override
    public String getValue() {
      return value;
    }

    /** Deliberately omits the value, so that logging cannot leak a secret. */
    @Override
    public String toString() {
      return "ResolvedSecret{reference='" + reference + "'}";
    }
  }

  public static final class ResolutionErrorImpl implements ResolutionError {

    private final String reference;
    private final SecretErrorCode code;
    private final String message;

    public ResolutionErrorImpl(
        final String reference, final SecretErrorCode code, final String message) {
      this.reference = reference;
      this.code = code;
      this.message = message;
    }

    @Override
    public String getReference() {
      return reference;
    }

    @Override
    public SecretErrorCode getCode() {
      return code;
    }

    @Override
    public String getMessage() {
      return message;
    }

    /**
     * Deliberately omits the message, so that logging cannot leak a secret. The reference and the
     * typed code are enough to triage a failure, whereas the message is server-provided free text
     * the client cannot vet. It stays available through {@link #getMessage()}.
     */
    @Override
    public String toString() {
      return "ResolutionError{reference='" + reference + "', code=" + code + '}';
    }
  }
}
