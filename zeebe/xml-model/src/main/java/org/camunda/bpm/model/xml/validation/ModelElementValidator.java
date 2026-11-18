/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.model.xml.validation;

import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

/**
 * A validator for model element instances.
 *
 * @see ModelInstance#validate(java.util.Collection)
 * @param <T> the type of the elements to validate.
 * @since 7.6
 */
public interface ModelElementValidator<T extends ModelElementInstance> {

  /**
   * <p>The type of the element this validator is applied to. The validator is applied to all
   * instances implementing this type.</p>
   *
   * <p>Example from BPMN: Assume the type returned is 'Task'. Then the validator is invoked for
   * all instances of task, including instances of 'ServiceTask', 'UserTask', ...</p>
   *
   * @return the type of the element this validator is applied to.
   */
  Class<T> getElementType();

  /**
   * Validate an element.
   *
   * @param element the element to validate
   * @param validationResultCollector object used to collect validation results for this element.
   */
  void validate(T element, ValidationResultCollector validationResultCollector);
}
