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
package io.camunda.client;

import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.grpc.Metadata;
import java.io.IOException;

/** Implementations of this interface must be thread-safe. */
public interface CredentialsProvider {

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
   * @return a builder to configure authentication use basic auth
   */
  static BasicAuthCredentialsProviderBuilder newBasicAuthCredentialsProviderBuilder() {
    return new BasicAuthCredentialsProviderBuilder();
  }

  /**
   * Used to apply call credentials on a per-request basis, abstracting over gRPC and REST. This
   * interface is only meant to be consumed, not implemented externally.
   */
  interface CredentialsApplier {

    /**
     * Puts the given header key and value into the request headers (e.g. HTTP headers or gRPC
     * metadata).
     *
     * @param key the header key
     * @param value the header value
     */
    void put(final String key, final String value);

    /**
     * Helper method to build a credentials applier out of gRPC metadata.
     *
     * @param metadata the gRPC metadata on which to apply
     */
    static CredentialsApplier ofMetadata(final Metadata metadata) {
      return (key, value) ->
          metadata.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);
    }
  }

  /**
   * Represents the result of a failed call, abstracting over gRPC and standard HTTP status codes.
   */
  interface StatusCode {

    /** Returns the raw status code, e.g. HTTP 401 Unauthorized, or gRPC 16 Unauthenticated. */
    int code();

    /** Returns true if the request was failed because the user cannot be authenticated. */
    boolean isUnauthorized();
  }
}
