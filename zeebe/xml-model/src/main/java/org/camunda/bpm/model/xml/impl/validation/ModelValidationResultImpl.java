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
package org.camunda.bpm.model.xml.impl.validation;

import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ValidationResult;
import org.camunda.bpm.model.xml.validation.ValidationResultType;

/**
 * @author Daniel Meyer
 *
 */
public class ModelValidationResultImpl implements ValidationResult {

  protected int code;
  protected ValidationResultType type;
  protected ModelElementInstance element;
  protected String message;

  public ModelValidationResultImpl(ModelElementInstance element, ValidationResultType type, int code, String message) {
    this.element = element;
    this.type = type;
    this.code = code;
    this.message = message;
  }

  @Override
  public ValidationResultType getType() {
    return type;
  }

  @Override
  public ModelElementInstance getElement() {
    return element;
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public int getCode() {
    return code;
  }

}
