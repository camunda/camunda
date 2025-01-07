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
package io.camunda.zeebe.client;

import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import java.io.IOException;

/** Implementations of this interface must be thread-safe. */
public interface CredentialsProvider extends io.camunda.client.CredentialsProvider {

  /**
   * Adds credentials to the headers. For an example of this, see {@link
   * OAuthCredentialsProvider#applyCredentials(CredentialsApplier)}
   *
   * @param applier where to add the credentials headers
   */
  void applyCredentials(final CredentialsApplier applier) throws IOException;

  /**
   * Returns true if the request should be retried; otherwise returns false. For an example of this,
   * see {@link OAuthCredentialsProvider#shouldRetryRequest(StatusCode)}.
   *
   * <p><strong>Only called for REST calls</strong>
   *
   * @param statusCode the response code for the failure
   */
  boolean shouldRetryRequest(final StatusCode statusCode);

  /**
   * @return a builder to configure and create a new {@link OAuthCredentialsProvider}.
   */
  static OAuthCredentialsProviderBuilder newCredentialsProviderBuilder() {
    return new OAuthCredentialsProviderBuilder();
  }

  /**
   * Used to apply call credentials on a per-request basis, abstracting over gRPC and REST. This
   * interface is only meant to be consumed, not implemented externally.
   */
  interface CredentialsApplier extends io.camunda.client.CredentialsProvider.CredentialsApplier {}

  /**
   * Represents the result of a failed call, abstracting over gRPC and standard HTTP status codes.
   */
  interface StatusCode extends io.camunda.client.CredentialsProvider.StatusCode {}
}
