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
package io.camunda.zeebe.model.bpmn.instance.zeebe;

import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;

/** A Zeebe extension for an ad-hoc sub-process. */
public interface ZeebeAdHoc extends BpmnModelElementInstance {

  /**
   * @return the collection of elements that should be activated when entering the ad-hoc
   *     sub-process.
   */
  String getActiveElementsCollection();

  /**
   * Sets the collection of elements that should be activated when entering the ad-hoc sub-process.
   *
   * @param activateElements the collection of element to be activated
   */
  void setActiveElementsCollection(final String activateElements);

  /**
   * @return the implementation type of the ad-hoc sub-process
   */
  ZeebeAdHocImplementationType getImplementationType();

  /**
   * Sets the implementation type of the ad-hoc sub-process.
   *
   * @param implementationType the implementation type
   */
  void setImplementationType(ZeebeAdHocImplementationType implementationType);

  /**
   * @return the variable name of the output collection
   */
  String getOutputCollection();

  /**
   * Sets the variable name of the output collection used to collect outputs of the element
   * activation
   *
   * @param outputCollection the variable name of the output collection
   */
  void setOutputCollection(String outputCollection);

  /**
   * @return the output element expression
   */
  String getOutputElement();

  /**
   * Sets the output element expression.
   *
   * @param outputElement the output element expression
   */
  void setOutputElement(String outputElement);
}
