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
package io.camunda.process.test.impl.runtime;

public class ContainerRuntimePorts {

  // Zeebe
  public static final int ZEEBE_COMMAND_API = 26501;
  public static final int ZEEBE_GATEWAY_API = 26500;
  public static final int ZEEBE_INTERNAL_API = 26502;
  public static final int ZEEBE_MONITORING_API = 9600;
  public static final int ZEEBE_REST_API = 8080;

  // Elasticsearch
  public static final int ELASTICSEARCH_REST_API = 9200;

  // Operate
  public static final int OPERATE_REST_API = 8080;

  // Tasklist
  public static final int TASKLIST_REST_API = 8080;
}
