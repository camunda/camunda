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
package io.camunda.client.auditlog;

import static io.camunda.client.util.assertions.SortAssert.assertSort;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.AuditLogFilter;
import io.camunda.client.protocol.rest.AuditLogSearchQueryRequest;
import io.camunda.client.protocol.rest.AuditLogSearchQueryResult;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class SearchAuditLogTest extends ClientRestTest {

  @Test
  public void shouldSearchAuditLogsWithEmptyQuery() {
    // when
    gatewayService.onSearchAuditLogRequest(Instancio.create(AuditLogSearchQueryResult.class));
    client.newAuditLogSearchRequest().send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getAuditLogSearchUrl());
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  public void shouldSearchAuditLogsWithArgs() {
    // when
    client
        .newAuditLogSearchRequest()
        .filter(fn -> fn.processInstanceKey("processInstanceKey"))
        .sort(s -> s.timestamp().desc())
        .page(fn -> fn.limit(10))
        .send()
        .join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/audit-logs/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  public void shouldSearchAuditLogsWithFullFilters() {
    // when
    client
        .newAuditLogSearchRequest()
        .filter(
            fn ->
                fn.auditLogKey("auditLogKey")
                    .entityKey("entityKey")
                    .processInstanceKey("processInstanceKey")
                    .processDefinitionKey("processDefinitionKey")
                    .elementInstanceKey("elementInstanceKey")
                    .operationType(AuditLogOperationTypeEnum.CREATE)
                    .result(AuditLogResultEnum.SUCCESS)
                    .timestamp(OffsetDateTime.MIN)
                    .actorId("actorId")
                    .actorType(AuditLogActorTypeEnum.CLIENT)
                    .entityType(AuditLogEntityTypeEnum.BATCH)
                    .tenantId("tenantId")
                    .category(AuditLogCategoryEnum.ADMIN)
                    .deploymentKey("deploymentKey")
                    .formKey("formKey")
                    .resourceKey("resourceKey")
                    .processDefinitionId("processDefinitionId")
                    .jobKey("jobKey")
                    .userTaskKey("userTaskKey")
                    .decisionRequirementsId("decisionRequirementsId")
                    .decisionRequirementsKey("decisionRequirementsKey")
                    .decisionDefinitionId("decisionDefinitionId")
                    .decisionDefinitionKey("decisionDefinitionKey")
                    .decisionEvaluationKey("decisionEvaluationKey")
                    .relatedEntityKey("relatedEntityKey")
                    .relatedEntityType(AuditLogEntityTypeEnum.USER)
                    .entityDescription("entityDescription"))
        .send()
        .join();

    // then
    final AuditLogSearchQueryRequest request =
        gatewayService.getLastRequest(AuditLogSearchQueryRequest.class);
    final AuditLogFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getAuditLogKey().get$Eq()).isEqualTo("auditLogKey");
    assertThat(filter.getEntityKey().get$Eq()).isEqualTo("entityKey");
    assertThat(filter.getProcessInstanceKey().get$Eq()).isEqualTo("processInstanceKey");
    assertThat(filter.getProcessDefinitionId().get$Eq()).isEqualTo("processDefinitionId");
    assertThat(filter.getProcessDefinitionKey().get$Eq()).isEqualTo("processDefinitionKey");
    assertThat(filter.getElementInstanceKey().get$Eq()).isEqualTo("elementInstanceKey");
    assertThat(filter.getJobKey().get$Eq()).isEqualTo("jobKey");
    assertThat(filter.getUserTaskKey().get$Eq()).isEqualTo("userTaskKey");
    assertThat(filter.getOperationType().get$Eq().getValue()).isEqualTo("CREATE");
    assertThat(filter.getResult().get$Eq().getValue()).isEqualTo("SUCCESS");
    assertThat(filter.getTimestamp().get$Eq()).isEqualTo(OffsetDateTime.MIN.toString());
    assertThat(filter.getActorId().get$Eq()).isEqualTo("actorId");
    assertThat(filter.getActorType().get$Eq().getValue()).isEqualTo("CLIENT");
    assertThat(filter.getEntityType().get$Eq().getValue()).isEqualTo("BATCH");
    assertThat(filter.getTenantId().get$Eq()).isEqualTo("tenantId");
    assertThat(filter.getCategory().get$Eq().getValue()).isEqualTo("ADMIN");
    assertThat(filter.getDecisionRequirementsId().get$Eq()).isEqualTo("decisionRequirementsId");
    assertThat(filter.getDecisionRequirementsKey().get$Eq()).isEqualTo("decisionRequirementsKey");
    assertThat(filter.getDecisionDefinitionId().get$Eq()).isEqualTo("decisionDefinitionId");
    assertThat(filter.getDecisionDefinitionKey().get$Eq()).isEqualTo(("decisionDefinitionKey"));
    assertThat(filter.getDecisionEvaluationKey().get$Eq()).isEqualTo(("decisionEvaluationKey"));
    assertThat(filter.getDeploymentKey().get$Eq()).isEqualTo("deploymentKey");
    assertThat(filter.getFormKey().get$Eq()).isEqualTo("formKey");
    assertThat(filter.getResourceKey().get$Eq()).isEqualTo("resourceKey");
    assertThat(filter.getRelatedEntityKey().get$Eq()).isEqualTo("relatedEntityKey");
    assertThat(filter.getRelatedEntityType().get$Eq().getValue()).isEqualTo("USER");
    assertThat(filter.getEntityDescription().get$Eq()).isEqualTo("entityDescription");
  }

  @Test
  public void shouldSearchAuditLogsWithAdvancedFilters() {
    // when
    client
        .newAuditLogSearchRequest()
        .filter(
            fn ->
                fn.auditLogKey(f -> f.eq("auditLogKey"))
                    .processInstanceKey(f -> f.notIn("processInstanceKey"))
                    .processDefinitionKey(f -> f.eq("processDefinitionKey"))
                    .elementInstanceKey(f -> f.exists(true))
                    .operationType(
                        f -> f.in(Collections.singletonList(AuditLogOperationTypeEnum.CREATE)))
                    .result(f -> f.neq(AuditLogResultEnum.SUCCESS))
                    .timestamp(f -> f.gt(OffsetDateTime.MIN))
                    .actorId(f -> f.eq("actorId"))
                    .actorType(f -> f.like("CLIENT"))
                    .entityType(f -> f.eq(AuditLogEntityTypeEnum.BATCH))
                    .tenantId(f -> f.eq("tenantId"))
                    .category(f -> f.eq(AuditLogCategoryEnum.ADMIN))
                    .deploymentKey(f -> f.eq("deploymentKey"))
                    .formKey(f -> f.eq("formKey"))
                    .resourceKey(f -> f.eq("resourceKey"))
                    .processDefinitionId(f -> f.eq("processDefinitionId"))
                    .jobKey(f -> f.in("jobKey"))
                    .userTaskKey(f -> f.eq("userTaskKey"))
                    .decisionRequirementsId(f -> f.eq("decisionRequirementsId"))
                    .decisionRequirementsKey(f -> f.notIn("decisionRequirementsKey"))
                    .decisionDefinitionId(f -> f.eq("decisionDefinitionId"))
                    .decisionDefinitionKey(f -> f.eq("decisionDefinitionKey"))
                    .decisionEvaluationKey(f -> f.eq("decisionEvaluationKey"))
                    .relatedEntityKey(f -> f.eq("relatedEntityKey"))
                    .relatedEntityType(f -> f.eq(AuditLogEntityTypeEnum.USER))
                    .entityDescription(f -> f.eq("entityDescription")))
        .send()
        .join();

    // then
    final AuditLogSearchQueryRequest request =
        gatewayService.getLastRequest(AuditLogSearchQueryRequest.class);
    final AuditLogFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getAuditLogKey().get$Eq()).isEqualTo("auditLogKey");
    assertThat(filter.getProcessInstanceKey().get$NotIn().get(0)).isEqualTo("processInstanceKey");
    assertThat(filter.getProcessDefinitionId().get$Eq()).isEqualTo("processDefinitionId");
    assertThat(filter.getProcessDefinitionKey().get$Eq()).isEqualTo("processDefinitionKey");
    assertThat(filter.getElementInstanceKey().get$Exists()).isEqualTo(true);
    assertThat(filter.getJobKey().get$In().get(0)).isEqualTo("jobKey");
    assertThat(filter.getUserTaskKey().get$Eq()).isEqualTo("userTaskKey");
    assertThat(filter.getOperationType().get$In().get(0).getValue()).isEqualTo("CREATE");
    assertThat(filter.getResult().get$Neq().getValue()).isEqualTo("SUCCESS");
    assertThat(filter.getTimestamp().get$Gt()).isEqualTo(OffsetDateTime.MIN.toString());
    assertThat(filter.getActorId().get$Eq()).isEqualTo("actorId");
    assertThat(filter.getActorType().get$Like()).isEqualTo("CLIENT");
    assertThat(filter.getEntityType().get$Eq().getValue()).isEqualTo("BATCH");
    assertThat(filter.getTenantId().get$Eq()).isEqualTo("tenantId");
    assertThat(filter.getCategory().get$Eq().getValue()).isEqualTo("ADMIN");
    assertThat(filter.getDecisionRequirementsId().get$Eq()).isEqualTo("decisionRequirementsId");
    assertThat(filter.getDecisionRequirementsKey().get$NotIn().get(0))
        .isEqualTo("decisionRequirementsKey");
    assertThat(filter.getDecisionDefinitionId().get$Eq()).isEqualTo("decisionDefinitionId");
    assertThat(filter.getDecisionDefinitionKey().get$Eq()).isEqualTo(("decisionDefinitionKey"));
    assertThat(filter.getDecisionEvaluationKey().get$Eq()).isEqualTo(("decisionEvaluationKey"));
    assertThat(filter.getDeploymentKey().get$Eq()).isEqualTo("deploymentKey");
    assertThat(filter.getFormKey().get$Eq()).isEqualTo("formKey");
    assertThat(filter.getResourceKey().get$Eq()).isEqualTo("resourceKey");
    assertThat(filter.getRelatedEntityKey().get$Eq()).isEqualTo("relatedEntityKey");
    assertThat(filter.getRelatedEntityType().get$Eq().getValue()).isEqualTo("USER");
    assertThat(filter.getEntityDescription().get$Eq()).isEqualTo("entityDescription");
  }

  @Test
  void shouldSearchAuditLogsWithFullSorting() {
    // when
    client
        .newAuditLogSearchRequest()
        .sort(
            s ->
                s.actorId()
                    .asc()
                    .actorType()
                    .desc()
                    .annotation()
                    .asc()
                    .auditLogKey()
                    .asc()
                    .batchOperationKey()
                    .desc()
                    .batchOperationType()
                    .desc()
                    .category()
                    .asc()
                    .decisionDefinitionId()
                    .asc()
                    .decisionDefinitionKey()
                    .asc()
                    .decisionEvaluationKey()
                    .asc()
                    .decisionRequirementsId()
                    .asc()
                    .decisionRequirementsKey()
                    .desc()
                    .elementInstanceKey()
                    .asc()
                    .entityKey()
                    .desc()
                    .entityType()
                    .asc()
                    .jobKey()
                    .desc()
                    .operationType()
                    .asc()
                    .processDefinitionId()
                    .asc()
                    .processDefinitionKey()
                    .asc()
                    .processInstanceKey()
                    .asc()
                    .result()
                    .asc()
                    .tenantId()
                    .asc()
                    .timestamp()
                    .desc()
                    .userTaskKey()
                    .asc())
        .send()
        .join();

    // then
    final AuditLogSearchQueryRequest request =
        gatewayService.getLastRequest(AuditLogSearchQueryRequest.class);
    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromAuditLogSearchQuerySortRequest(
            Objects.requireNonNull(request.getSort()));
    assertThat(sorts).hasSize(24);
    assertSort(sorts.get(0), "actorId", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "actorType", SortOrderEnum.DESC);
    assertSort(sorts.get(2), "annotation", SortOrderEnum.ASC);
    assertSort(sorts.get(3), "auditLogKey", SortOrderEnum.ASC);
    assertSort(sorts.get(4), "batchOperationKey", SortOrderEnum.DESC);
    assertSort(sorts.get(5), "batchOperationType", SortOrderEnum.DESC);
    assertSort(sorts.get(6), "category", SortOrderEnum.ASC);
    assertSort(sorts.get(7), "decisionDefinitionId", SortOrderEnum.ASC);
    assertSort(sorts.get(8), "decisionDefinitionKey", SortOrderEnum.ASC);
    assertSort(sorts.get(9), "decisionEvaluationKey", SortOrderEnum.ASC);
    assertSort(sorts.get(10), "decisionRequirementsId", SortOrderEnum.ASC);
    assertSort(sorts.get(11), "decisionRequirementsKey", SortOrderEnum.DESC);
    assertSort(sorts.get(12), "elementInstanceKey", SortOrderEnum.ASC);
    assertSort(sorts.get(13), "entityKey", SortOrderEnum.DESC);
    assertSort(sorts.get(14), "entityType", SortOrderEnum.ASC);
    assertSort(sorts.get(15), "jobKey", SortOrderEnum.DESC);
    assertSort(sorts.get(16), "operationType", SortOrderEnum.ASC);
    assertSort(sorts.get(17), "processDefinitionId", SortOrderEnum.ASC);
    assertSort(sorts.get(18), "processDefinitionKey", SortOrderEnum.ASC);
    assertSort(sorts.get(19), "processInstanceKey", SortOrderEnum.ASC);
    assertSort(sorts.get(20), "result", SortOrderEnum.ASC);
    assertSort(sorts.get(21), "tenantId", SortOrderEnum.ASC);
    assertSort(sorts.get(22), "timestamp", SortOrderEnum.DESC);
    assertSort(sorts.get(23), "userTaskKey", SortOrderEnum.ASC);
  }

  @Test
  void shouldIncludeSortAndFilterInAuditLogSearchRequestBody() {
    // when
    client
        .newAuditLogSearchRequest()
        .filter(fn -> fn.actorId("actorId"))
        .sort(s -> s.auditLogKey().desc())
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest lastRequest = gatewayService.getLastRequest();
    final String requestBody = lastRequest.getBodyAsString();

    assertThat(requestBody).contains("\"sort\":[{\"field\":\"auditLogKey\",\"order\":\"DESC\"}]");
    assertThat(requestBody).contains("\"filter\":{\"actorId\"");
  }
}
