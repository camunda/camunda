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
package io.camunda.zeebe.client.impl;

public final class ZeebeClientEnvironmentVariables {
  public static final String PLAINTEXT_CONNECTION_VAR = "ZEEBE_INSECURE_CONNECTION";
  public static final String CA_CERTIFICATE_VAR = "ZEEBE_CA_CERTIFICATE_PATH";
  public static final String KEEP_ALIVE_VAR = "ZEEBE_KEEP_ALIVE";
  public static final String OVERRIDE_AUTHORITY_VAR = "ZEEBE_OVERRIDE_AUTHORITY";
  public static final String ZEEBE_CLIENT_WORKER_STREAM_ENABLED =
      "ZEEBE_CLIENT_WORKER_STREAM_ENABLED";
  public static final String REST_ADDRESS_VAR = "ZEEBE_REST_ADDRESS";
  public static final String GRPC_ADDRESS_VAR = "ZEEBE_GRPC_ADDRESS";
  public static final String PREFER_REST_VAR = "ZEEBE_PREFER_REST";
  public static final String DEFAULT_TENANT_ID_VAR = "ZEEBE_DEFAULT_TENANT_ID";
  public static final String DEFAULT_JOB_WORKER_TENANT_IDS_VAR =
      "ZEEBE_DEFAULT_JOB_WORKER_TENANT_IDS";
  public static final String USE_DEFAULT_RETRY_POLICY_VAR = "ZEEBE_CLIENT_USE_DEFAULT_RETRY_POLICY";

  /** OAuth Environment Variables */
  public static final String OAUTH_ENV_CLIENT_ID = "ZEEBE_CLIENT_ID";

  public static final String OAUTH_ENV_CLIENT_SECRET = "ZEEBE_CLIENT_SECRET";
  public static final String OAUTH_ENV_TOKEN_AUDIENCE = "ZEEBE_TOKEN_AUDIENCE";
  public static final String OAUTH_ENV_TOKEN_SCOPE = "ZEEBE_TOKEN_SCOPE";
  public static final String OAUTH_ENV_AUTHORIZATION_SERVER = "ZEEBE_AUTHORIZATION_SERVER_URL";
  public static final String OAUTH_ENV_SSL_CLIENT_KEYSTORE_PATH = "ZEEBE_SSL_CLIENT_KEYSTORE_PATH";
  public static final String OAUTH_ENV_SSL_CLIENT_KEYSTORE_SECRET =
      "ZEEBE_SSL_CLIENT_KEYSTORE_SECRET";
  public static final String OAUTH_ENV_SSL_CLIENT_KEYSTORE_KEY_SECRET =
      "ZEEBE_SSL_CLIENT_KEYSTORE_KEY_SECRET";
  public static final String OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_PATH =
      "ZEEBE_SSL_CLIENT_TRUSTSTORE_PATH";
  public static final String OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_SECRET =
      "ZEEBE_SSL_CLIENT_TRUSTSTORE_SECRET";
  public static final String OAUTH_ENV_CACHE_PATH = "ZEEBE_CLIENT_CONFIG_PATH";
  public static final String OAUTH_ENV_CONNECT_TIMEOUT = "ZEEBE_AUTH_CONNECT_TIMEOUT";
  public static final String OAUTH_ENV_READ_TIMEOUT = "ZEEBE_AUTH_READ_TIMEOUT";

  private ZeebeClientEnvironmentVariables() {}
}
