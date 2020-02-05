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
package io.zeebe.client;

import io.grpc.Metadata;
import io.zeebe.client.impl.oauth.OAuthCredentialsProvider;
import io.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import java.io.IOException;

public interface CredentialsProvider {

  /**
   * Adds credentials to the headers. For an example of this, see {@link
   * OAuthCredentialsProvider#applyCredentials(Metadata)}
   *
   * @param headers gRPC headers to be modified
   */
  void applyCredentials(Metadata headers) throws IOException;

  /**
   * Returns true if the request should be retried; otherwise returns false. For an example of this,
   * see {@link OAuthCredentialsProvider#shouldRetryRequest(Throwable)}
   *
   * @param throwable error that caused the request to fail
   */
  boolean shouldRetryRequest(Throwable throwable);

  /** @return a builder to configure and create a new {@link OAuthCredentialsProvider}. */
  static OAuthCredentialsProviderBuilder newCredentialsProviderBuilder() {
    return new OAuthCredentialsProviderBuilder();
  }
}
