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
package io.camunda.process.test.impl.coverage.results;

import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import java.util.List;
import org.immutables.value.Value;

/**
 * Coverage input data for a single process instance execution.
 *
 * <p>Contains the process instance together with visited element instances and traversed sequence
 * flows.
 */
@Value.Immutable
public interface CoverageProcessInstanceData {

  /** Returns the process instance metadata. */
  ProcessInstance getProcessInstance();

  /** Returns element instances visited during execution of the process instance. */
  List<ElementInstance> getElementInstances();

  /** Returns sequence flows traversed during execution of the process instance. */
  List<ProcessInstanceSequenceFlow> getSequenceFlows();
}
