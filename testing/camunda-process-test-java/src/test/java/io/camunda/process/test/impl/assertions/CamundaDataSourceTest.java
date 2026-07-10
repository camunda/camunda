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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.search.filter.CorrelatedMessageSubscriptionFilter;
import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.filter.MessageSubscriptionFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.request.CorrelatedMessageSubscriptionSearchRequest;
import io.camunda.client.api.search.request.DecisionInstanceSearchRequest;
import io.camunda.client.api.search.request.ElementInstanceSearchRequest;
import io.camunda.client.api.search.request.IncidentSearchRequest;
import io.camunda.client.api.search.request.MessageSubscriptionSearchRequest;
import io.camunda.client.api.search.request.ProcessInstanceSearchRequest;
import io.camunda.client.api.search.request.UserTaskSearchRequest;
import io.camunda.client.api.search.response.CorrelatedMessageSubscription;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.MessageSubscription;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import java.time.Instant;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CamundaDataSourceTest {

  private static final Instant START_TIME = Instant.parse("2024-01-01T10:00:00Z");

  @Mock private CamundaClient client;

  @Nested
  class ProcessInstanceTests {

    @Mock(answer = Answers.RETURNS_SELF)
    private ProcessInstanceSearchRequest searchRequest;

    @Mock private CamundaFuture<SearchResponse<ProcessInstance>> future;
    @Mock private SearchResponse<ProcessInstance> searchResponse;

    @Mock(answer = Answers.RETURNS_SELF)
    private ProcessInstanceFilter processInstanceFilter;

    @Captor private ArgumentCaptor<Consumer<ProcessInstanceFilter>> filterCaptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
      when(client.newProcessInstanceSearchRequest()).thenReturn(searchRequest);
      when(searchRequest.send()).thenReturn(future);
      when(future.join()).thenReturn(searchResponse);
      when(searchResponse.items()).thenReturn(Collections.emptyList());
    }

    @Test
    void shouldNotApplyStartTimeFilterWhenNotSet() {
      // given
      final CamundaDataSource dataSource = new CamundaDataSource(client);

      // when
      dataSource.findProcessInstances();

      // then
      verify(searchRequest).filter(filterCaptor.capture());
      filterCaptor.getValue().accept(processInstanceFilter);
      verify(processInstanceFilter, never()).startDate(any(Consumer.class));
    }

    @Test
    void shouldApplyStartTimeFilterToProcessInstances() {
      // given
      final CamundaDataSource dataSource = new CamundaDataSource(client, START_TIME);

      // when
      dataSource.findProcessInstances();

      // then
      verify(searchRequest).filter(filterCaptor.capture());
      filterCaptor.getValue().accept(processInstanceFilter);
      verify(processInstanceFilter).startDate(any(Consumer.class));
    }

    @Test
    void shouldApplyStartTimeFilterBeforeUserFilter() {
      // given
      final CamundaDataSource dataSource = new CamundaDataSource(client, START_TIME);

      // when - user provides a custom filter AND start time is set
      dataSource.findProcessInstances(f -> f.processDefinitionId("myProcess"));

      // then
      verify(searchRequest).filter(filterCaptor.capture());
      filterCaptor.getValue().accept(processInstanceFilter);

      // The start time filter should be applied in addition to the user's filter
      verify(processInstanceFilter).startDate(any(Consumer.class));
      verify(processInstanceFilter).processDefinitionId("myProcess");
    }
  }

  @Nested
  class ElementInstanceTests {

    @Mock(answer = Answers.RETURNS_SELF)
    private ElementInstanceSearchRequest searchRequest;

    @Mock private CamundaFuture<SearchResponse<ElementInstance>> future;
    @Mock private SearchResponse<ElementInstance> searchResponse;

    @Mock(answer = Answers.RETURNS_SELF)
    private ElementInstanceFilter elementInstanceFilter;

    @Captor private ArgumentCaptor<Consumer<ElementInstanceFilter>> filterCaptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
      when(client.newElementInstanceSearchRequest()).thenReturn(searchRequest);
      when(searchRequest.send()).thenReturn(future);
      when(future.join()).thenReturn(searchResponse);
      when(searchResponse.items()).thenReturn(Collections.emptyList());
    }

    @Test
    void shouldNotApplyStartTimeFilterWhenNotSet() {
      // given
      final CamundaDataSource dataSource = new CamundaDataSource(client);

      // when
      dataSource.findElementInstances(f -> {});

      // then
      verify(searchRequest).filter(filterCaptor.capture());
      filterCaptor.getValue().accept(elementInstanceFilter);
      verify(elementInstanceFilter, never()).startDate(any(Consumer.class));
    }

    @Test
    void shouldApplyStartTimeFilterToElementInstances() {
      // given
      final CamundaDataSource dataSource = new CamundaDataSource(client, START_TIME);

      // when
      dataSource.findElementInstances(f -> {});

      // then
      verify(searchRequest).filter(filterCaptor.capture());
      filterCaptor.getValue().accept(elementInstanceFilter);
      verify(elementInstanceFilter).startDate(any(Consumer.class));
    }
  }

  @Nested
  class IncidentTests {

    @Mock(answer = Answers.RETURNS_SELF)
    private IncidentSearchRequest searchRequest;

    @Mock private CamundaFuture<SearchResponse<Incident>> future;
    @Mock private SearchResponse<Incident> searchResponse;

    @Mock(answer = Answers.RETURNS_SELF)
    private IncidentFilter incidentFilter;

    @Captor private ArgumentCaptor<Consumer<IncidentFilter>> filterCaptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
      when(client.newIncidentSearchRequest()).thenReturn(searchRequest);
      when(searchRequest.send()).thenReturn(future);
      when(future.join()).thenReturn(searchResponse);
      when(searchResponse.items()).thenReturn(Collections.emptyList());
    }

    @Test
    void shouldNotApplyStartTimeFilterWhenNotSet() {
      // given
      final CamundaDataSource dataSource = new CamundaDataSource(client);

      // when
      dataSource.findIncidents(f -> {});

      // then
      verify(searchRequest).filter(filterCaptor.capture());
      filterCaptor.getValue().accept(incidentFilter);
      verify(incidentFilter, never()).creationTime(any(Consumer.class));
    }

    @Test
    void shouldApplyStartTimeFilterToIncidents() {
      // given
      final CamundaDataSource dataSource = new CamundaDataSource(client, START_TIME);

      // when
      dataSource.findIncidents(f -> {});

      // then
      verify(searchRequest).filter(filterCaptor.capture());
      filterCaptor.getValue().accept(incidentFilter);
      verify(incidentFilter).creationTime(any(Consumer.class));
    }
  }

  @Nested
  class UserTaskTests {

    @Mock(answer = Answers.RETURNS_SELF)
    private UserTaskSearchRequest searchRequest;

    @Mock private CamundaFuture<SearchResponse<UserTask>> future;
    @Mock private SearchResponse<UserTask> searchResponse;

    @Mock(answer = Answers.RETURNS_SELF)
    private UserTaskFilter userTaskFilter;

    @Captor private ArgumentCaptor<Consumer<UserTaskFilter>> filterCaptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
      when(client.newUserTaskSearchRequest()).thenReturn(searchRequest);
      when(searchRequest.send()).thenReturn(future);
      when(future.join()).thenReturn(searchResponse);
      when(searchResponse.items()).thenReturn(Collections.emptyList());
    }

    @Test
    void shouldNotApplyStartTimeFilterWhenNotSet() {
      // given
      final CamundaDataSource dataSource = new CamundaDataSource(client);

      // when
      dataSource.findUserTasks(f -> {});

      // then
      verify(searchRequest).filter(filterCaptor.capture());
      filterCaptor.getValue().accept(userTaskFilter);
      verify(userTaskFilter, never()).creationDate(any(Consumer.class));
    }

    @Test
    void shouldApplyStartTimeFilterToUserTasks() {
      // given
      final CamundaDataSource dataSource = new CamundaDataSource(client, START_TIME);

      // when
      dataSource.findUserTasks(f -> {});

      // then
      verify(searchRequest).filter(filterCaptor.capture());
      filterCaptor.getValue().accept(userTaskFilter);
      verify(userTaskFilter).creationDate(any(Consumer.class));
    }
  }

  @Nested
  class DecisionInstanceTests {

    @Mock(answer = Answers.RETURNS_SELF)
    private DecisionInstanceSearchRequest searchRequest;

    @Mock private CamundaFuture<SearchResponse<DecisionInstance>> future;
    @Mock private SearchResponse<DecisionInstance> searchResponse;

    @Mock(answer = Answers.RETURNS_SELF)
    private DecisionInstanceFilter decisionInstanceFilter;

    @Captor private ArgumentCaptor<Consumer<DecisionInstanceFilter>> filterCaptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
      when(client.newDecisionInstanceSearchRequest()).thenReturn(searchRequest);
      when(searchRequest.send()).thenReturn(future);
      when(future.join()).thenReturn(searchResponse);
      when(searchResponse.items()).thenReturn(Collections.emptyList());
    }

    @Test
    void shouldNotApplyStartTimeFilterWhenNotSet() {
      // given
      final CamundaDataSource dataSource = new CamundaDataSource(client);

      // when
      dataSource.findDecisionInstances(f -> {});

      // then
      verify(searchRequest).filter(filterCaptor.capture());
      filterCaptor.getValue().accept(decisionInstanceFilter);
      verify(decisionInstanceFilter, never()).evaluationDate(any(Consumer.class));
    }

    @Test
    void shouldApplyStartTimeFilterToDecisionInstances() {
      // given
      final CamundaDataSource dataSource = new CamundaDataSource(client, START_TIME);

      // when
      dataSource.findDecisionInstances(f -> {});

      // then
      verify(searchRequest).filter(filterCaptor.capture());
      filterCaptor.getValue().accept(decisionInstanceFilter);
      verify(decisionInstanceFilter).evaluationDate(any(Consumer.class));
    }
  }

  @Nested
  class MessageSubscriptionTests {

    @Mock(answer = Answers.RETURNS_SELF)
    private MessageSubscriptionSearchRequest searchRequest;

    @Mock private CamundaFuture<SearchResponse<MessageSubscription>> future;
    @Mock private SearchResponse<MessageSubscription> searchResponse;

    @Mock(answer = Answers.RETURNS_SELF)
    private MessageSubscriptionFilter messageSubscriptionFilter;

    @Captor private ArgumentCaptor<Consumer<MessageSubscriptionFilter>> filterCaptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
      when(client.newMessageSubscriptionSearchRequest()).thenReturn(searchRequest);
      when(searchRequest.send()).thenReturn(future);
      when(future.join()).thenReturn(searchResponse);
      when(searchResponse.items()).thenReturn(Collections.emptyList());
    }

    @Test
    void shouldNotApplyStartTimeFilterWhenNotSet() {
      // given
      final CamundaDataSource dataSource = new CamundaDataSource(client);

      // when
      dataSource.findMessageSubscriptions(f -> {});

      // then
      verify(searchRequest).filter(filterCaptor.capture());
      filterCaptor.getValue().accept(messageSubscriptionFilter);
      verify(messageSubscriptionFilter, never()).lastUpdatedDate(any(Consumer.class));
    }

    @Test
    void shouldApplyStartTimeFilterToMessageSubscriptions() {
      // given
      final CamundaDataSource dataSource = new CamundaDataSource(client, START_TIME);

      // when
      dataSource.findMessageSubscriptions(f -> {});

      // then
      verify(searchRequest).filter(filterCaptor.capture());
      filterCaptor.getValue().accept(messageSubscriptionFilter);
      verify(messageSubscriptionFilter).lastUpdatedDate(any(Consumer.class));
    }
  }

  @Nested
  class CorrelatedMessageTests {

    @Mock(answer = Answers.RETURNS_SELF)
    private CorrelatedMessageSubscriptionSearchRequest searchRequest;

    @Mock private CamundaFuture<SearchResponse<CorrelatedMessageSubscription>> future;
    @Mock private SearchResponse<CorrelatedMessageSubscription> searchResponse;

    @Mock(answer = Answers.RETURNS_SELF)
    private CorrelatedMessageSubscriptionFilter correlatedMessageFilter;

    @Captor private ArgumentCaptor<Consumer<CorrelatedMessageSubscriptionFilter>> filterCaptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
      when(client.newCorrelatedMessageSubscriptionSearchRequest()).thenReturn(searchRequest);
      when(searchRequest.send()).thenReturn(future);
      when(future.join()).thenReturn(searchResponse);
      when(searchResponse.items()).thenReturn(Collections.emptyList());
    }

    @Test
    void shouldNotApplyStartTimeFilterWhenNotSet() {
      // given
      final CamundaDataSource dataSource = new CamundaDataSource(client);

      // when
      dataSource.findCorrelatedMessages(f -> {});

      // then
      verify(searchRequest).filter(filterCaptor.capture());
      filterCaptor.getValue().accept(correlatedMessageFilter);
      verify(correlatedMessageFilter, never()).correlationTime(any(Consumer.class));
    }

    @Test
    void shouldApplyStartTimeFilterToCorrelatedMessages() {
      // given
      final CamundaDataSource dataSource = new CamundaDataSource(client, START_TIME);

      // when
      dataSource.findCorrelatedMessages(f -> {});

      // then
      verify(searchRequest).filter(filterCaptor.capture());
      filterCaptor.getValue().accept(correlatedMessageFilter);
      verify(correlatedMessageFilter).correlationTime(any(Consumer.class));
    }
  }
}
