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

public class ContainerRuntimeDefaults {

  public static final String ELASTICSEARCH_DOCKER_IMAGE_NAME = "elasticsearch";
  public static final String ZEEBE_DOCKER_IMAGE_NAME = "camunda/zeebe";
  public static final String OPERATE_DOCKER_IMAGE_NAME = "camunda/operate";
  public static final String TASKLIST_DOCKER_IMAGE_NAME = "camunda/tasklist";

  public static final String ELASTICSEARCH_LOGGER_NAME = "tc.elasticsearch";
  public static final String ZEEBE_LOGGER_NAME = "tc.zeebe";
  public static final String OPERATE_LOGGER_NAME = "tc.operate";
  public static final String TASKLIST_LOGGER_NAME = "tc.tasklist";

  private static final ContainerRuntimeVersionUtil VERSION_UTIL =
      ContainerRuntimeVersionUtil.readVersions();

  public static final String ELASTICSEARCH_DOCKER_IMAGE_VERSION =
      VERSION_UTIL.getElasticsearchVersion();
  public static final String CAMUNDA_VERSION = VERSION_UTIL.getCamundaVersion();
  public static final String ZEEBE_DOCKER_IMAGE_VERSION = CAMUNDA_VERSION;
  public static final String OPERATE_DOCKER_IMAGE_VERSION = CAMUNDA_VERSION;
  public static final String TASKLIST_DOCKER_IMAGE_VERSION = CAMUNDA_VERSION;
}
