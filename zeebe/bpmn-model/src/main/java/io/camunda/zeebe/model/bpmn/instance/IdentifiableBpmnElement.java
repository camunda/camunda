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

package io.camunda.zeebe.model.bpmn.instance;

/**
 * Represents a BPMN model element that has a unique identifier.
 *
 * @author Giampaolo Orru
 */
public interface IdentifiableBpmnElement extends BpmnModelElementInstance {

  /**
   * Gets the id of this BPMN element.
   *
   * @return the element's unique identifier
   */
  String getId();

  /**
   * Sets the id for this BPMN element.
   *
   * @param id the unique identifier to set
   */
  void setId(String id);
}
