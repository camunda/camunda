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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.request.ProcessInstanceSearchRequest;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import java.time.Instant;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CamundaDataSourceTest {

  @Mock private CamundaClient client;

  @Mock(answer = Answers.RETURNS_SELF)
  private ProcessInstanceSearchRequest searchRequest;

  @Mock private CamundaFuture<SearchResponse<ProcessInstance>> future;
  @Mock private SearchResponse<ProcessInstance> searchResponse;

  @Mock(answer = Answers.RETURNS_SELF)
  private ProcessInstanceFilter processInstanceFilter;

  @Captor private ArgumentCaptor<Consumer<ProcessInstanceFilter>> filterCaptor;

  private CamundaDataSource dataSource;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    dataSource = new CamundaDataSource(client);
    when(client.newProcessInstanceSearchRequest()).thenReturn(searchRequest);
    when(searchRequest.send()).thenReturn(future);
    when(future.join()).thenReturn(searchResponse);
    when(searchResponse.items()).thenReturn(Collections.emptyList());
  }

  @Test
  void shouldNotApplyStartTimeFilterWhenNotSet() {
    // when
    dataSource.findProcessInstances();

    // then - verify the filter consumer was applied but without start time
    verify(searchRequest).filter(filterCaptor.capture());

    // Apply the captured filter to a mock ProcessInstanceFilter
    filterCaptor.getValue().accept(processInstanceFilter);

    // No startDate filter should be applied
    verify(processInstanceFilter, never()).startDate(any(Consumer.class));
  }

  @Test
  void shouldApplyStartTimeFilterToProcessInstances() {
    // given
    final Instant startTime = Instant.parse("2024-01-01T10:00:00Z");
    dataSource.setTestCaseStartTime(startTime);

    // when
    dataSource.findProcessInstances();

    // then - verify the filter consumer was applied with start time
    verify(searchRequest).filter(filterCaptor.capture());

    // Apply the captured filter to a mock ProcessInstanceFilter
    filterCaptor.getValue().accept(processInstanceFilter);

    // startDate filter should be applied
    verify(processInstanceFilter).startDate(any(Consumer.class));
  }

  @Test
  void shouldApplyStartTimeFilterWithUserFilter() {
    // given
    final Instant startTime = Instant.parse("2024-01-01T10:00:00Z");
    dataSource.setTestCaseStartTime(startTime);

    final ProcessInstanceFilter userFilter =
        mock(ProcessInstanceFilter.class, Answers.RETURNS_SELF);

    // when - user provides a custom filter AND start time is set
    dataSource.findProcessInstances(f -> userFilter.processDefinitionId("myProcess"));

    // then
    verify(searchRequest).filter(filterCaptor.capture());
    filterCaptor.getValue().accept(processInstanceFilter);

    // The start time filter should be applied in addition to the user's filter
    verify(processInstanceFilter).startDate(any(Consumer.class));
  }
}
