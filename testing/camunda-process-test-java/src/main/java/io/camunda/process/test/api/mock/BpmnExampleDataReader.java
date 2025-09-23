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
package io.camunda.process.test.api.mock;

import java.util.Map;

/** Reads a BPMN model and extracts variables from an element's example data. */
public interface BpmnExampleDataReader {

  /**
   * Transforms an element's example data into a variable map.
   *
   * @param processDefinitionKey the BPMN model's definition key
   * @param elementId the id of the element containing the example data
   * @return a map containing the example data
   */
  Map<String, Object> readExampleData(final long processDefinitionKey, final String elementId);

  class BpmnExampleDataReadException extends RuntimeException {

    public BpmnExampleDataReadException(final String message) {
      super(message);
    }

    public BpmnExampleDataReadException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
