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
package io.zeebe.protocol.impl.record.value.deployment;

public enum ResourceType {
  BPMN_XML,
  YAML_WORKFLOW;

  public static ResourceType getResourceType(String resourceName) {
    resourceName = resourceName.toLowerCase();

    if (resourceName.endsWith(".yaml")) {
      return ResourceType.YAML_WORKFLOW;
    } else if (resourceName.endsWith(".bpmn") || resourceName.endsWith(".bpmn20.xml")) {
      return ResourceType.BPMN_XML;
    } else {
      throw new RuntimeException(
          String.format(
              "Expected to resolve type of resource '%s', but could not; should be a .bpmn or .yaml file",
              resourceName));
    }
  }
}
