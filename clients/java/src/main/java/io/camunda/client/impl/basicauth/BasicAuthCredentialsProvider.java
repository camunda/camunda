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
package io.camunda.client.impl.basicauth;

import io.camunda.client.CredentialsProvider;
import java.util.Base64;

public class BasicAuthCredentialsProvider implements CredentialsProvider {

  public static final String AUTH_HEADER_KEY = "Authorization";
  private final String authHeaderValue;

  public BasicAuthCredentialsProvider(final String username, final String password) {
    final String base64EncodedCredentials =
        Base64.getEncoder().encodeToString(String.format("%s:%s", username, password).getBytes());
    authHeaderValue = String.format("Basic %s", base64EncodedCredentials);
  }

  @Override
  public void applyCredentials(final CredentialsApplier applier) {
    applier.put(AUTH_HEADER_KEY, authHeaderValue);
  }

  @Override
  public boolean shouldRetryRequest(final StatusCode statusCode) {
    return false;
  }
}
