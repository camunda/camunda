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
package io.camunda.process.test.api.coverage;

import io.camunda.client.api.search.response.DecisionDefinition;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import java.util.List;
import java.util.Map;

public interface CoverageDataSource {

  List<ProcessInstance> getProcessInstances();

  Map<Long, List<ElementInstance>> getElementInstancesByProcessInstanceKey();

  Map<Long, List<ProcessInstanceSequenceFlow>> getSequenceFlowsByProcessInstanceKey();

  Map<String, ProcessDefinition> getProcessDefinitionsByProcessDefinitionId();

  Map<String, String> getProcessDefinitionXmlByProcessDefinitionId();

  List<DecisionInstance> getDecisionInstances();

  Map<String, DecisionInstance> getDecisionInstancesByDecisionInstanceId();

  Map<String, DecisionDefinition> getDecisionDefinitionsByDecisionDefinitionId();

  Map<Long, String> getDecisionDefinitionXmlByDecisionDefinitionKey();
}
