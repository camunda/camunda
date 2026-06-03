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

import io.camunda.client.api.search.response.DecisionDefinition;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import io.camunda.process.test.impl.assertions.CamundaTestResults;
import io.camunda.process.test.impl.coverage.data.CoverageTestData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CoverageTestDataSnapshot implements CoverageTestData {

  private final List<ProcessInstance> processInstances;
  private final Map<Long, List<ElementInstance>> elementInstancesByProcessInstanceKey;
  private final Map<Long, List<ProcessInstanceSequenceFlow>> sequenceFlowsByProcessInstanceKey;
  private final Map<String, ProcessDefinition> processDefinitionsByProcessDefinitionId;
  private final Map<String, String> processDefinitionXmlByProcessDefinitionId;
  private final List<DecisionInstance> decisionInstances;
  private final Map<String, DecisionInstance> decisionInstancesByDecisionInstanceId;
  private final Map<String, DecisionDefinition> decisionDefinitionsByDecisionDefinitionId;
  private final Map<Long, String> decisionDefinitionXmlByDecisionDefinitionKey;

  private CoverageTestDataSnapshot(
      final List<ProcessInstance> processInstances,
      final Map<Long, List<ElementInstance>> elementInstancesByProcessInstanceKey,
      final Map<Long, List<ProcessInstanceSequenceFlow>> sequenceFlowsByProcessInstanceKey,
      final Map<String, ProcessDefinition> processDefinitionsByProcessDefinitionId,
      final Map<String, String> processDefinitionXmlByProcessDefinitionId,
      final List<DecisionInstance> decisionInstances,
      final Map<String, DecisionInstance> decisionInstancesByDecisionInstanceId,
      final Map<String, DecisionDefinition> decisionDefinitionsByDecisionDefinitionId,
      final Map<Long, String> decisionDefinitionXmlByDecisionDefinitionKey) {
    this.processInstances = processInstances;
    this.elementInstancesByProcessInstanceKey = elementInstancesByProcessInstanceKey;
    this.sequenceFlowsByProcessInstanceKey = sequenceFlowsByProcessInstanceKey;
    this.processDefinitionsByProcessDefinitionId = processDefinitionsByProcessDefinitionId;
    this.processDefinitionXmlByProcessDefinitionId = processDefinitionXmlByProcessDefinitionId;
    this.decisionInstances = decisionInstances;
    this.decisionInstancesByDecisionInstanceId = decisionInstancesByDecisionInstanceId;
    this.decisionDefinitionsByDecisionDefinitionId = decisionDefinitionsByDecisionDefinitionId;
    this.decisionDefinitionXmlByDecisionDefinitionKey =
        decisionDefinitionXmlByDecisionDefinitionKey;
  }

  public static CoverageTestDataSnapshot from(final CamundaTestResults dataSource) {
    final List<ProcessInstance> processInstances = dataSource.findProcessInstances();
    final Map<Long, List<ElementInstance>> elementInstancesByProcessInstanceKey =
        processInstances.stream()
            .collect(
                Collectors.toMap(
                    ProcessInstance::getProcessInstanceKey,
                    pi ->
                        dataSource.findElementInstancesByProcessInstanceKey(
                            pi.getProcessInstanceKey()),
                    (a, b) -> a));
    final Map<Long, List<ProcessInstanceSequenceFlow>> sequenceFlowsByProcessInstanceKey =
        processInstances.stream()
            .collect(
                Collectors.toMap(
                    ProcessInstance::getProcessInstanceKey,
                    pi ->
                        dataSource.findSequenceFlowsByProcessInstanceKey(
                            pi.getProcessInstanceKey()),
                    (a, b) -> a));

    final Map<String, ProcessDefinition> processDefinitionsByProcessDefinitionId = new HashMap<>();
    final Map<String, String> processDefinitionXmlByProcessDefinitionId = new HashMap<>();
    processInstances.forEach(
        pi -> {
          processDefinitionsByProcessDefinitionId.computeIfAbsent(
              pi.getProcessDefinitionId(), dataSource::findProcessDefinitionByProcessDefinitionId);
          processDefinitionXmlByProcessDefinitionId.computeIfAbsent(
              pi.getProcessDefinitionId(),
              dataSource::getProcessDefinitionXmlByProcessDefinitionId);
        });

    final List<DecisionInstance> decisionInstances = dataSource.findDecisionInstances(f -> {});
    final Map<String, DecisionInstance> decisionInstancesByDecisionInstanceId =
        decisionInstances.stream()
            .collect(
                Collectors.toMap(
                    DecisionInstance::getDecisionInstanceId,
                    di -> dataSource.getDecisionInstance(di.getDecisionInstanceId()),
                    (a, b) -> a));
    final Map<String, DecisionDefinition> decisionDefinitionsByDecisionDefinitionId =
        new HashMap<>();
    final Map<Long, String> decisionDefinitionXmlByDecisionDefinitionKey = new HashMap<>();
    decisionInstances.forEach(
        di -> {
          final DecisionDefinition decisionDefinition =
              decisionDefinitionsByDecisionDefinitionId.computeIfAbsent(
                  di.getDecisionDefinitionId(),
                  dataSource::findDecisionDefinitionByDecisionDefinitionId);
          decisionDefinitionXmlByDecisionDefinitionKey.computeIfAbsent(
              decisionDefinition.getDecisionKey(),
              dataSource::getDecisionDefinitionXmlByDecisionDefinitionKey);
        });

    return new CoverageTestDataSnapshot(
        processInstances,
        elementInstancesByProcessInstanceKey,
        sequenceFlowsByProcessInstanceKey,
        processDefinitionsByProcessDefinitionId,
        processDefinitionXmlByProcessDefinitionId,
        decisionInstances,
        decisionInstancesByDecisionInstanceId,
        decisionDefinitionsByDecisionDefinitionId,
        decisionDefinitionXmlByDecisionDefinitionKey);
  }

  public static CoverageTestDataSnapshot empty() {
    return new CoverageTestDataSnapshot(
        java.util.Collections.emptyList(),
        java.util.Collections.emptyMap(),
        java.util.Collections.emptyMap(),
        java.util.Collections.emptyMap(),
        java.util.Collections.emptyMap(),
        java.util.Collections.emptyList(),
        java.util.Collections.emptyMap(),
        java.util.Collections.emptyMap(),
        java.util.Collections.emptyMap());
  }

  @Override
  public List<ProcessInstance> getProcessInstances() {
    return processInstances;
  }

  @Override
  public Map<Long, List<ElementInstance>> getElementInstancesByProcessInstanceKey() {
    return elementInstancesByProcessInstanceKey;
  }

  @Override
  public Map<Long, List<ProcessInstanceSequenceFlow>> getSequenceFlowsByProcessInstanceKey() {
    return sequenceFlowsByProcessInstanceKey;
  }

  @Override
  public Map<String, ProcessDefinition> getProcessDefinitionsByProcessDefinitionId() {
    return processDefinitionsByProcessDefinitionId;
  }

  @Override
  public Map<String, String> getProcessDefinitionXmlByProcessDefinitionId() {
    return processDefinitionXmlByProcessDefinitionId;
  }

  @Override
  public List<DecisionInstance> getDecisionInstances() {
    return decisionInstances;
  }

  @Override
  public Map<String, DecisionInstance> getDecisionInstancesByDecisionInstanceId() {
    return decisionInstancesByDecisionInstanceId;
  }

  @Override
  public Map<String, DecisionDefinition> getDecisionDefinitionsByDecisionDefinitionId() {
    return decisionDefinitionsByDecisionDefinitionId;
  }

  @Override
  public Map<Long, String> getDecisionDefinitionXmlByDecisionDefinitionKey() {
    return decisionDefinitionXmlByDecisionDefinitionKey;
  }
}
