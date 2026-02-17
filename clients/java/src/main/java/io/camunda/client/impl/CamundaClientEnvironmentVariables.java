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
package io.camunda.client.impl;

public final class CamundaClientEnvironmentVariables {
  public static final String CA_CERTIFICATE_VAR = "CAMUNDA_CA_CERTIFICATE_PATH";
  public static final String KEEP_ALIVE_VAR = "CAMUNDA_KEEP_ALIVE";
  public static final String OVERRIDE_AUTHORITY_VAR = "CAMUNDA_OVERRIDE_AUTHORITY";
  public static final String CAMUNDA_CLIENT_WORKER_STREAM_ENABLED =
      "CAMUNDA_CLIENT_WORKER_STREAM_ENABLED";
  public static final String REST_ADDRESS_VAR = "CAMUNDA_REST_ADDRESS";
  public static final String GRPC_ADDRESS_VAR = "CAMUNDA_GRPC_ADDRESS";
  public static final String PREFER_REST_VAR = "CAMUNDA_PREFER_REST";
  public static final String MAX_HTTP_CONNECTIONS = "CAMUNDA_MAX_HTTP_CONNECTIONS";
  public static final String DEFAULT_TENANT_ID_VAR = "CAMUNDA_DEFAULT_TENANT_ID";
  public static final String DEFAULT_JOB_WORKER_TENANT_IDS_VAR =
      "CAMUNDA_DEFAULT_JOB_WORKER_TENANT_IDS";
  public static final String DEFAULT_JOB_WORKER_TENANT_FILTER_MODE_VAR =
      "CAMUNDA_DEFAULT_JOB_WORKER_TENANT_FILTER_MODE";
  public static final String USE_DEFAULT_RETRY_POLICY_VAR =
      "CAMUNDA_CLIENT_USE_DEFAULT_RETRY_POLICY";

  /** OAuth Environment Variables */
  public static final String OAUTH_ENV_CLIENT_ID = "CAMUNDA_CLIENT_ID";

  public static final String OAUTH_ENV_CLIENT_SECRET = "CAMUNDA_CLIENT_SECRET";
  public static final String OAUTH_ENV_TOKEN_AUDIENCE = "CAMUNDA_TOKEN_AUDIENCE";
  public static final String OAUTH_ENV_TOKEN_SCOPE = "CAMUNDA_TOKEN_SCOPE";
  public static final String OAUTH_ENV_TOKEN_RESOURCE = "CAMUNDA_TOKEN_RESOURCE";
  public static final String OAUTH_ENV_AUTHORIZATION_SERVER = "CAMUNDA_AUTHORIZATION_SERVER_URL";
  public static final String OAUTH_ENV_WELL_KNOWN_CONFIGURATION_URL =
      "CAMUNDA_WELL_KNOWN_CONFIGURATION_URL";
  public static final String OAUTH_ENV_ISSUER_URL = "CAMUNDA_ISSUER_URL";
  public static final String OAUTH_ENV_SSL_CLIENT_KEYSTORE_PATH =
      "CAMUNDA_SSL_CLIENT_KEYSTORE_PATH";
  public static final String OAUTH_ENV_SSL_CLIENT_KEYSTORE_SECRET =
      "CAMUNDA_SSL_CLIENT_KEYSTORE_SECRET";
  public static final String OAUTH_ENV_SSL_CLIENT_KEYSTORE_KEY_SECRET =
      "CAMUNDA_SSL_CLIENT_KEYSTORE_KEY_SECRET";
  public static final String OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_PATH =
      "CAMUNDA_SSL_CLIENT_TRUSTSTORE_PATH";
  public static final String OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_SECRET =
      "CAMUNDA_SSL_CLIENT_TRUSTSTORE_SECRET";
  public static final String OAUTH_ENV_CACHE_PATH = "CAMUNDA_CLIENT_CONFIG_PATH";
  public static final String OAUTH_ENV_CONNECT_TIMEOUT = "CAMUNDA_AUTH_CONNECT_TIMEOUT";
  public static final String OAUTH_ENV_READ_TIMEOUT = "CAMUNDA_AUTH_READ_TIMEOUT";
  public static final String OAUTH_ENV_CLIENT_ASSERTION_KEYSTORE_PATH =
      "CAMUNDA_CLIENT_ASSERTION_KEYSTORE_PATH";
  public static final String OAUTH_ENV_CLIENT_ASSERTION_KEYSTORE_PASSWORD =
      "CAMUNDA_CLIENT_ASSERTION_KEYSTORE_PASSWORD";
  public static final String OAUTH_ENV_CLIENT_ASSERTION_KEYSTORE_KEY_ALIAS =
      "CAMUNDA_CLIENT_ASSERTION_KEYSTORE_KEY_ALIAS";
  public static final String OAUTH_ENV_CLIENT_ASSERTION_KEYSTORE_KEY_PASSWORD =
      "CAMUNDA_CLIENT_ASSERTION_KEYSTORE_KEY_PASSWORD";

  /** Basic Auth Environment Variables */
  public static final String BASIC_AUTH_ENV_USERNAME = "CAMUNDA_BASIC_AUTH_USERNAME";

  public static final String BASIC_AUTH_ENV_PASSWORD = "CAMUNDA_BASIC_AUTH_PASSWORD";

  private CamundaClientEnvironmentVariables() {}
}
