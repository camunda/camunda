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
package io.zeebe.gateway.impl.configuration;

public class EnvironmentConstants {

  public static final String ENV_GATEWAY_HOST = "ZEEBE_GATEWAY_HOST";
  public static final String ENV_GATEWAY_PORT = "ZEEBE_GATEWAY_PORT";
  public static final String ENV_GATEWAY_TRANSPORT_BUFFER = "ZEEBE_GATEWAY_TRANSPORT_BUFFER";
  public static final String ENV_GATEWAY_REQUEST_TIMEOUT = "ZEEBE_GATEWAY_REQUEST_TIMEOUT";
  public static final String ENV_GATEWAY_MANAGEMENT_THREADS = "ZEEBE_GATEWAY_MANAGEMENT_THREADS";
  public static final String ENV_GATEWAY_CONTACT_POINT = "ZEEBE_GATEWAY_CONTACT_POINT";
  public static final String ENV_GATEWAY_CLUSTER_NAME = "ZEEBE_GATEWAY_CLUSTER_NAME";
  public static final String ENV_GATEWAY_CLUSTER_MEMBER_ID = "ZEEBE_GATEWAY_CLUSTER_MEMBER_ID";
  public static final String ENV_GATEWAY_CLUSTER_HOST = "ZEEBE_GATEWAY_CLUSTER_HOST";
  public static final String ENV_GATEWAY_CLUSTER_PORT = "ZEEBE_GATEWAY_CLUSTER_PORT";
}
