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

public class ContainerRuntimeEnvs {

  // Zeebe
  public static final String ZEEBE_ENV_ELASTICSEARCH_CLASSNAME =
      "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME";
  public static final String ZEEBE_ENV_ELASTICSEARCH_ARGS_URL =
      "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL";
  public static final String ZEEBE_ENV_ELASTICSEARCH_ARGS_BULK_SIZE =
      "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE";
  public static final String ZEEBE_ENV_CLOCK_CONTROLLED = "ZEEBE_CLOCK_CONTROLLED";

  // Elasticsearch
  public static final String ELASTICSEARCH_ENV_XPACK_SECURITY_ENABLED = "xpack.security.enabled";

  // Operate
  public static final String OPERATE_ENV_ZEEBE_GATEWAYADDRESS =
      "CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS";
  public static final String OPERATE_ENV_ELASTICSEARCH_URL = "CAMUNDA_OPERATE_ELASTICSEARCH_URL";
  public static final String OPERATE_ENV_ZEEBEELASTICSEARCH_URL =
      "CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL";
  public static final String CAMUNDA_OPERATE_IMPORTER_READERBACKOFF =
      "CAMUNDA_OPERATE_IMPORTER_READERBACKOFF";

  // Tasklist
  public static final String TASKLIST_ENV_ZEEBE_GATEWAYADDRESS =
      "CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS";
  public static final String TASKLIST_ENV_ZEEBE_RESTADDRESS = "CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS";
  public static final String TASKLIST_ENV_ELASTICSEARCH_URL = "CAMUNDA_TASKLIST_ELASTICSEARCH_URL";
  public static final String TASKLIST_ENV_ZEEBEELASTICSEARCH_URL =
      "CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL";
  public static final String TASKLIST_CSRF_PREVENTION_ENABLED =
      "CAMUNDA_TASKLIST_CSRFPREVENTIONENABLED";
}
