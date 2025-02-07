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
package io.camunda.zeebe.spring.common.exception;

import io.camunda.spring.client.exception.CamundaBpmnError;
import java.util.Map;

/**
 * Indicates an error in sense of BPMN occured, that should be handled by the BPMN process, see <a
 * href="https://docs.camunda.io/docs/reference/bpmn-processes/error-events/error-events/">...</a>
 */
@Deprecated(since = "8.8", forRemoval = true)
public class ZeebeBpmnError extends CamundaBpmnError {

  public ZeebeBpmnError(
      final String errorCode, final String errorMessage, final Map<String, Object> variables) {
    super(errorCode, errorMessage, variables);
  }
}
