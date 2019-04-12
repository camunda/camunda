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

public class ConfigurationDefaults {

  public static final String DEFAULT_HOST = "0.0.0.0";
  public static final int DEFAULT_PORT = 26500;
  public static final String DEFAULT_CONTACT_POINT_HOST = "127.0.0.1";
  public static final String DEFAULT_TRANSPORT_BUFFER_SIZE = "128M";
  public static final int DEFAULT_CONTACT_POINT_PORT = 26505;
  public static final int DEFAULT_MANAGEMENT_THREADS = 1;
  public static final String DEFAULT_REQUEST_TIMEOUT = "15s";
  public static final String DEFAULT_CLUSTER_NAME = "zeebe-cluster";
  public static final String DEFAULT_CLUSTER_MEMBER_ID = "gateway";
  public static final String DEFAULT_CLUSTER_HOST = "0.0.0.0";
  public static final int DEFAULT_CLUSTER_PORT = 26499;
}
