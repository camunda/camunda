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
package io.zeebe.exporter.record.value.deployment;

/** Lists the different types of deploy-able resources */
public enum DeploymentResourceType {
  /**
   * Implies the resource blob is a BPMN XML document.
   *
   * @see <a href="https://docs.zeebe.io/bpmn-workflows/README.html">
   *     https://docs.zeebe.io/bpmn-workflows/README.html</a>
   */
  BPMN_XML,
  /**
   * Implies the resource blob is a YAML document. See {@see
   * @see <a href="https://docs.zeebe.io/yaml-workflows/README.html">
   *   https://docs.zeebe.io/yaml-workflows/README.html</a>
   */
  YAML_WORKFLOW;
}
