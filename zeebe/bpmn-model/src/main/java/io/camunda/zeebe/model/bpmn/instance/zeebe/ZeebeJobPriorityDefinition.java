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

/**
 * Zeebe job priority for worker-activated jobs.
 *
 * <p>The priority value can be specified either as an integer literal in the 32-bit signed integer
 * range, or as a FEEL expression prefixed with {@code =}. The default literal priority is {@code
 * 0}.
 *
 * <p>Applied on {@code <bpmn:process>} (process-wide default) or on a job-creating task (task-level
 * override).
 *
 * <p>Expression-based priorities are represented and stored as their raw string value, including
 * the leading {@code =}, and are exposed via {@link #getPriority()} and {@link
 * #setPriority(String)}.
 */
public interface ZeebeJobPriorityDefinition extends BpmnModelElementInstance {

  String DEFAULT_LITERAL_PRIORITY = "0";

  String getPriority();

  void setPriority(String priority);
}
