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
package io.camunda.process.test.impl.extensions;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;

/** Shared BPMN process model for conditional behavior IT tests. */
final class ConditionalBehaviorTestProcess {

  static final String PROCESS_ID = "user-happiness-check";
  static final String USER_TASK_ID = "State_Happiness";
  static final String SERVICE_TASK_ID = "Export_Happiness";
  static final String JOB_TYPE = "io.camunda:http-json:1";

  static final BpmnModelInstance MODEL =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .userTask(USER_TASK_ID)
          .zeebeUserTask()
          .exclusiveGateway("User_Happy_Gateway")
          .conditionExpression("=happy")
          .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType(JOB_TYPE).zeebeJobRetries("3"))
          .endEvent()
          .moveToLastExclusiveGateway()
          .defaultFlow()
          .connectTo(USER_TASK_ID)
          .done();

  private ConditionalBehaviorTestProcess() {}
}
