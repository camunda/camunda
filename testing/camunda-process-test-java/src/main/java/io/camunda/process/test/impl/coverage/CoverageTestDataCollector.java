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
package io.camunda.process.test.impl.coverage;

import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.coverage.data.CoverageTestData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageDecisionDefinitionData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageDecisionInstanceData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageProcessDefinitionData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageProcessInstanceData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageTestData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageTestData.Builder;
import java.util.List;

public class CoverageTestDataCollector {

  public static CoverageTestData collectData(final CamundaDataSource dataSource) {
    final Builder builder = ImmutableCoverageTestData.builder();

    final List<ProcessInstance> processInstances = dataSource.findProcessInstances();

    processInstances.stream()
        .map(
            processInstance ->
                ImmutableCoverageProcessInstanceData.builder()
                    .processInstance(processInstance)
                    .addAllElementInstances(
                        dataSource.findElementInstancesByProcessInstanceKey(
                            processInstance.getProcessInstanceKey()))
                    .addAllSequenceFlows(
                        dataSource.findSequenceFlowsByProcessInstanceKey(
                            processInstance.getProcessInstanceKey()))
                    .build())
        .forEach(builder::addProcessInstanceData);

    // a definition per deployment that ran, not per id: a test can run several deployments of one
    // id, for example when a mock deploys a stub of a process that is also deployed for real
    processInstances.stream()
        .map(ProcessInstance::getProcessDefinitionKey)
        .distinct()
        .map(
            processDefinitionKey ->
                ImmutableCoverageProcessDefinitionData.builder()
                    .processDefinition(
                        dataSource.getProcessDefinitionByProcessDefinitionKey(processDefinitionKey))
                    .xml(
                        dataSource.getProcessDefinitionXmlByProcessDefinitionKey(
                            processDefinitionKey))
                    .build())
        .forEach(builder::addProcessDefinitionData);

    final List<DecisionInstance> decisionInstances = dataSource.findDecisionInstances(f -> {});
    decisionInstances.stream()
        .map(
            decisionInstance ->
                // Load decision instance details
                dataSource.getDecisionInstance(decisionInstance.getDecisionInstanceId()))
        .map(
            decisionInstance ->
                ImmutableCoverageDecisionInstanceData.builder()
                    .decisionInstance(decisionInstance)
                    .build())
        .forEach(builder::addDecisionInstanceData);

    // a definition per deployment that was evaluated, not per id: a test can evaluate several
    // deployments of one id, for example when suites deploy fixtures that differ in their rules
    decisionInstances.stream()
        .map(DecisionInstance::getDecisionDefinitionKey)
        .distinct()
        .map(
            decisionDefinitionKey ->
                ImmutableCoverageDecisionDefinitionData.builder()
                    .decisionDefinition(
                        dataSource.getDecisionDefinitionByDecisionDefinitionKey(
                            decisionDefinitionKey))
                    .xml(
                        dataSource.getDecisionDefinitionXmlByDecisionDefinitionKey(
                            decisionDefinitionKey))
                    .build())
        .forEach(builder::addDecisionDefinitionData);

    return builder.build();
  }
}
