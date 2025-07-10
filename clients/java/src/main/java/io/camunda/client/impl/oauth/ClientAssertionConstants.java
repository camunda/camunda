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
package io.camunda.client.impl.oauth;

/** Constants for OAuth2 and certificate-based authentication. */
public final class ClientAssertionConstants {

  // OAuth2 client assertion constants
  public static final String CLIENT_ASSERTION_TYPE_JWT_BEARER =
      "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
  public static final String CLIENT_ASSERTION_TYPE_PARAM = "client_assertion_type";
  public static final String CLIENT_ASSERTION_PARAM = "client_assertion";

  // JWT header constants
  public static final String JWT_HEADER_ALG = "alg";
  public static final String JWT_HEADER_TYP = "typ";
  public static final String JWT_HEADER_X5T = "x5t";

  // JWT algorithms and types
  public static final String JWT_ALGORITHM_RS256 = "RS256";
  public static final String JWT_TYPE = "JWT";

  // Certificate constants
  public static final String KEYSTORE_TYPE_PKCS12 = "PKCS12";

  private ClientAssertionConstants() {}
}
