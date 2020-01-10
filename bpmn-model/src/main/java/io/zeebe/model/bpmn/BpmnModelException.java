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

package io.zeebe.model.bpmn;

import org.camunda.bpm.model.xml.ModelException;

/**
 * A {@link RuntimeException} in the Bpmn Model.
 *
 * @author Daniel Meyer
 */
public class BpmnModelException extends ModelException {

  private static final long serialVersionUID = 1L;

  public BpmnModelException() {}

  public BpmnModelException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public BpmnModelException(final String message) {
    super(message);
  }

  public BpmnModelException(final Throwable cause) {
    super(cause);
  }
}
