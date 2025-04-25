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
package io.camunda.client.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.protocol.rest.BaseProcessInstanceFilterFields;
import io.camunda.client.protocol.rest.BasicStringFilterProperty;
import io.camunda.client.protocol.rest.DateTimeFilterProperty;
import io.camunda.client.protocol.rest.ElementInstanceStateEnum;
import io.camunda.client.protocol.rest.ProcessDefinitionElementStatisticsQuery;
import io.camunda.client.protocol.rest.ProcessDefinitionStatisticsFilter;
import io.camunda.client.protocol.rest.ProcessInstanceStateEnum;
import io.camunda.client.protocol.rest.ProcessInstanceStateFilterProperty;
import io.camunda.client.protocol.rest.StringFilterProperty;
import io.camunda.client.protocol.rest.VariableValueFilterRequest;
import io.camunda.client.util.ClientRestTest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ProcessDefinitionStatisticsTest extends ClientRestTest {

  public static final long PROCESS_DEFINITION_KEY = 123L;

  @Test
  void shouldGetProcessDefinitionElementStatistics() {
    // when
    client.newProcessDefinitionElementStatisticsRequest(PROCESS_DEFINITION_KEY).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl())
        .isEqualTo(
            "/v2/process-definitions/" + PROCESS_DEFINITION_KEY + "/statistics/element-instances");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(request.getBodyAsString()).isEqualTo("{}");
  }

  @Test
  void shouldGetProcessDefinitionElementStatisticsWithFullFilters() {
    // when
    final OffsetDateTime startDate = OffsetDateTime.now().minusDays(1);
    final OffsetDateTime endDate = OffsetDateTime.now();
    final Map<String, Object> variablesMap = new LinkedHashMap<>();
    variablesMap.put("n1", "v1");
    variablesMap.put("n2", "v2");
    final List<VariableValueFilterRequest> variables =
        Arrays.asList(
            new VariableValueFilterRequest().name("n1").value(new StringFilterProperty().$eq("v1")),
            new VariableValueFilterRequest()
                .name("n2")
                .value(new StringFilterProperty().$eq("v2")));
    client
        .newProcessDefinitionElementStatisticsRequest(PROCESS_DEFINITION_KEY)
        .filter(
            f ->
                f.processInstanceKey(PROCESS_DEFINITION_KEY)
                    .parentProcessInstanceKey(25L)
                    .parentElementInstanceKey(30L)
                    .startDate(startDate)
                    .endDate(endDate)
                    .state(ProcessInstanceState.ACTIVE)
                    .hasIncident(true)
                    .tenantId("tenant")
                    .variables(variablesMap)
                    .batchOperationId("batchOperationId")
                    .errorMessage("Error message")
                    .hasRetriesLeft(true)
                    .elementId("elementId")
                    .elementInstanceState(ElementInstanceState.ACTIVE)
                    .hasElementInstanceIncident(true)
                    .incidentErrorHashCode(123456789))
        .send()
        .join();

    // then
    final ProcessDefinitionElementStatisticsQuery query =
        gatewayService.getLastRequest(ProcessDefinitionElementStatisticsQuery.class);
    final ProcessDefinitionStatisticsFilter filter = query.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getProcessInstanceKey().get$Eq())
        .isEqualTo(String.valueOf(PROCESS_DEFINITION_KEY));
    assertThat(filter.getParentProcessInstanceKey().get$Eq()).isEqualTo("25");
    assertThat(filter.getParentElementInstanceKey().get$Eq()).isEqualTo("30");
    assertThat(filter.getStartDate().get$Eq()).isEqualTo(startDate.toString());
    assertThat(filter.getEndDate().get$Eq()).isEqualTo(endDate.toString());
    assertThat(filter.getState().get$Eq()).isEqualTo(ProcessInstanceStateEnum.ACTIVE);
    assertThat(filter.getHasIncident()).isEqualTo(true);
    assertThat(filter.getTenantId().get$Eq()).isEqualTo("tenant");
    assertThat(filter.getVariables()).isEqualTo(variables);
    assertThat(filter.getBatchOperationId().get$Eq()).isEqualTo("batchOperationId");
    assertThat(filter.getErrorMessage().get$Eq()).isEqualTo("Error message");
    assertThat(filter.getHasRetriesLeft()).isEqualTo(true);
    assertThat(filter.getElementId().get$Eq()).isEqualTo("elementId");
    assertThat(filter.getElementInstanceState().get$Eq())
        .isEqualTo(ElementInstanceStateEnum.ACTIVE);
    assertThat(filter.getHasElementInstanceIncident()).isEqualTo(true);
    assertThat(filter.getIncidentErrorHashCode()).isEqualTo(123456789);
  }

  @Test
  void shouldGetProcessDefinitionElementStatisticsByProcessInstanceKeyLongFilter() {
    // when
    client
        .newProcessDefinitionElementStatisticsRequest(PROCESS_DEFINITION_KEY)
        .filter(f -> f.processInstanceKey(b -> b.in(1L, 10L)))
        .send()
        .join();

    // then
    final ProcessDefinitionElementStatisticsQuery request =
        gatewayService.getLastRequest(ProcessDefinitionElementStatisticsQuery.class);
    final ProcessDefinitionStatisticsFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    final BasicStringFilterProperty processInstanceKey = filter.getProcessInstanceKey();
    assertThat(processInstanceKey).isNotNull();
    assertThat(processInstanceKey.get$In()).isEqualTo(Arrays.asList("1", "10"));
  }

  @Test
  void shouldGetProcessDefinitionElementStatisticsByTenantIdStringFilter() {
    // when
    client
        .newProcessDefinitionElementStatisticsRequest(PROCESS_DEFINITION_KEY)
        .filter(f -> f.tenantId(b -> b.like("string")))
        .send()
        .join();

    // then
    final ProcessDefinitionElementStatisticsQuery request =
        gatewayService.getLastRequest(ProcessDefinitionElementStatisticsQuery.class);
    final ProcessDefinitionStatisticsFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    final StringFilterProperty tenantId = filter.getTenantId();
    assertThat(tenantId).isNotNull();
    assertThat(tenantId.get$Like()).isEqualTo("string");
  }

  @Test
  void shouldGetProcessDefinitionElementStatisticsByStartDateDateTimeFilter() {
    // when
    final OffsetDateTime now = OffsetDateTime.now();
    client
        .newProcessDefinitionElementStatisticsRequest(PROCESS_DEFINITION_KEY)
        .filter(f -> f.startDate(b -> b.gt(now)))
        .send()
        .join();

    // then
    final ProcessDefinitionElementStatisticsQuery request =
        gatewayService.getLastRequest(ProcessDefinitionElementStatisticsQuery.class);
    final ProcessDefinitionStatisticsFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    final DateTimeFilterProperty startDate = filter.getStartDate();
    assertThat(startDate).isNotNull();
    assertThat(startDate.get$Gt()).isEqualTo(now.toString());
  }

  @Test
  void shouldSearchProcessInstanceByVariablesFilter() {
    // given
    final Map<String, Object> variablesMap = new LinkedHashMap<>();
    variablesMap.put("n1", "v1");
    variablesMap.put("n2", "v2");
    final List<VariableValueFilterRequest> variables =
        Arrays.asList(
            new VariableValueFilterRequest().name("n1").value(new StringFilterProperty().$eq("v1")),
            new VariableValueFilterRequest()
                .name("n2")
                .value(new StringFilterProperty().$eq("v2")));

    // when
    client
        .newProcessDefinitionElementStatisticsRequest(PROCESS_DEFINITION_KEY)
        .filter(f -> f.variables(variablesMap))
        .send()
        .join();

    // then
    final ProcessDefinitionElementStatisticsQuery request =
        gatewayService.getLastRequest(ProcessDefinitionElementStatisticsQuery.class);
    final ProcessDefinitionStatisticsFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getVariables()).isEqualTo(variables);
  }

  @Test
  void shouldSearchProcessInstanceByOrFilters() {
    // when
    client
        .newProcessDefinitionElementStatisticsRequest(PROCESS_DEFINITION_KEY)
        .filter(
            f ->
                f.state(ProcessInstanceState.ACTIVE)
                    .orFilters(
                        Arrays.asList(
                            f1 -> f1.processInstanceKey(123L).elementId("elementId"),
                            f3 -> f3.hasElementInstanceIncident(true))))
        .send()
        .join();

    // then
    final ProcessDefinitionElementStatisticsQuery request =
        gatewayService.getLastRequest(ProcessDefinitionElementStatisticsQuery.class);
    final ProcessDefinitionStatisticsFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getState())
        .isEqualTo(new ProcessInstanceStateFilterProperty().$eq(ProcessInstanceStateEnum.ACTIVE));
    assertThat(filter.get$Or()).hasSize(2);
    assertThat(filter.get$Or())
        .containsExactlyInAnyOrder(
            new BaseProcessInstanceFilterFields()
                .processInstanceKey(new BasicStringFilterProperty().$eq("123"))
                .elementId(new StringFilterProperty().$eq("elementId")),
            new BaseProcessInstanceFilterFields().hasElementInstanceIncident(true));
  }
}
