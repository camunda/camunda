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
package io.camunda.process.test.impl.assertions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.filter.MessageSubscriptionFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.MessageSubscription;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import java.util.List;
import java.util.function.Consumer;

public class CamundaDataSource {

  private static final Consumer<SearchRequestPage> DEFAULT_PAGE_REQUEST = page -> page.limit(100);

  private final CamundaClient client;

  public CamundaDataSource(final CamundaClient client) {
    this.client = client;
  }

  public List<ElementInstance> findElementInstancesByProcessInstanceKey(
      final long processInstanceKey) {
    return findElementInstances(filter -> filter.processInstanceKey(processInstanceKey));
  }

  public List<ElementInstance> findElementInstances(final Consumer<ElementInstanceFilter> filter) {
    return client
        .newElementInstanceSearchRequest()
        .filter(filter)
        .sort(sort -> sort.startDate().asc())
        .page(DEFAULT_PAGE_REQUEST)
        .send()
        .join()
        .items();
  }

  public List<Variable> findGlobalVariablesByProcessInstanceKey(final long processInstanceKey) {
    return findVariables(
        filter -> filter.processInstanceKey(processInstanceKey).scopeKey(processInstanceKey));
  }

  public List<Variable> findVariables(final Consumer<VariableFilter> filter) {
    return client
        .newVariableSearchRequest()
        .filter(filter)
        .page(DEFAULT_PAGE_REQUEST)
        .send()
        .join()
        .items();
  }

  public Variable getVariable(final long variableKey) {
    return client.newVariableGetRequest(variableKey).send().join();
  }

  public List<ProcessInstance> findProcessInstances() {
    return findProcessInstances(filter -> {});
  }

  public List<ProcessInstance> findProcessInstances(final Consumer<ProcessInstanceFilter> filter) {
    return client
        .newProcessInstanceSearchRequest()
        .filter(filter)
        .sort(sort -> sort.startDate().asc())
        .page(DEFAULT_PAGE_REQUEST)
        .send()
        .join()
        .items();
  }

  public List<Incident> findIncidents(final Consumer<IncidentFilter> filter) {
    return client
        .newIncidentSearchRequest()
        .filter(filter)
        .sort(sort -> sort.creationTime().asc())
        .page(DEFAULT_PAGE_REQUEST)
        .send()
        .join()
        .items();
  }

  public List<UserTask> findUserTasks(final Consumer<UserTaskFilter> filter) {
    return client
        .newUserTaskSearchRequest()
        .filter(filter)
        .sort(sort -> sort.creationDate().asc())
        .page(DEFAULT_PAGE_REQUEST)
        .send()
        .join()
        .items();
  }

  public List<DecisionInstance> findDecisionInstances(
      final Consumer<DecisionInstanceFilter> filter) {
    return client.newDecisionInstanceSearchRequest().filter(filter).send().join().items();
  }

  public DecisionInstance getDecisionInstance(final String decisionInstanceId) {
    return client.newDecisionInstanceGetRequest(decisionInstanceId).send().join();
  }

  public List<MessageSubscription> getMessageSubscriptions() {
    return getMessageSubscriptions(filter -> {});
  }

  public List<MessageSubscription> getMessageSubscriptions(
      final Consumer<MessageSubscriptionFilter> filter) {
    return client
        .newMessageSubscriptionSearchRequest()
        .filter(filter)
        .sort(sort -> sort.lastUpdatedDate().asc())
        .page(DEFAULT_PAGE_REQUEST)
        .send()
        .join()
        .items();
  }
}
