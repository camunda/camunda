/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.cache;

import static io.camunda.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;
import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.TopHitsAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.AuthorizationsConfiguration;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.elasticsearch.ProcessStoreElasticSearch;
import io.camunda.tasklist.util.ElasticsearchTenantHelper;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.permission.TasklistPermissionServices;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessStoreElasticSearchTest {
  @Mock private ProcessIndex processIndex;
  @Mock private ElasticsearchClient es8Client;
  @Mock private ElasticsearchTenantHelper tenantHelper;
  @InjectMocks private ProcessStoreElasticSearch processStore;
  @Mock private ObjectMapper objectMapper;
  @InjectMocks private SpringContextHolder springContextHolder;
  @Mock private SecurityConfiguration securityConfiguration;
  @Mock private io.camunda.identity.autoconfigure.IdentityProperties identityProperties;
  @Mock private ResourceAccessProvider resourceAccessProvider;
  @Mock private CamundaAuthenticationProvider authenticationProvider;
  @InjectMocks private TasklistPermissionServices permissionServices;

  @BeforeEach
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(securityConfiguration.getMultiTenancy()).thenReturn(new MultiTenancyConfiguration());
    ReflectionTestUtils.setField(
        permissionServices, "authenticationProvider", authenticationProvider);
    ReflectionTestUtils.setField(
        permissionServices, "resourceAccessProvider", resourceAccessProvider);
    when(tenantHelper.makeQueryTenantAware(any(Query.class))).thenAnswer(i -> i.getArgument(0));
  }

  // ** Test Get Process by BPMN Process Id ** //
  @Test
  public void shouldReturnAProcessEntityWhenGetProcessByBpmnIdIsCalled() throws IOException {
    // given
    mockElasticSearchSuccess();

    // when
    final ProcessEntity result = processStore.getProcessByBpmnProcessId("bpmnProcessId");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("1");
  }

  @Test
  public void shouldReturnNotFoundWhenESReturnsZeroHits() throws IOException {
    // given
    mockElasticSearchNotFound();

    // when and then
    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> processStore.getProcessByBpmnProcessId("bpmnProcessId_not_exist"));
  }

  @Test
  public void shouldReturnIOExceptionForGetProcessByBpmnId() throws IOException {
    // given
    final IOException mockedException = new IOException("IO Exception during search");
    when(es8Client.search(any(SearchRequest.class), eq(ProcessEntity.class)))
        .thenThrow(mockedException);
    when(processIndex.getAlias()).thenReturn("alias");

    // when
    final TasklistRuntimeException thrown =
        assertThatExceptionOfType(TasklistRuntimeException.class)
            .isThrownBy(() -> processStore.getProcessByBpmnProcessId("bpmnProcessId"))
            .actual();

    // then
    assertThat(
            thrown
                .getMessage()
                .contains(
                    "Exception occurred, while obtaining the process: "
                        + mockedException.getMessage()))
        .isTrue();
  }

  // ** Test Get Process by Process Id ** //
  @Test
  public void shouldGetProcessReturnAProcessById() throws IOException {
    // when
    mockElasticSearchSuccess();

    // given
    final ProcessEntity result = processStore.getProcess("1");

    // then
    assertThat(result).isNotNull();
  }

  @Test
  public void shouldGetProcessReturnNotFound() throws IOException {
    // when
    mockElasticSearchNotFound();

    // given and then
    assertThatExceptionOfType(TasklistRuntimeException.class)
        .isThrownBy(() -> processStore.getProcess("processId"));
  }

  @Test
  public void shouldGetProcessReturnExceptionThrown() throws IOException {
    // when
    final String processId = "processId";
    final String errorMessage = "IOException error message";
    final IOException exception = new IOException(errorMessage);

    when(es8Client.search(any(SearchRequest.class), eq(ProcessEntity.class))).thenThrow(exception);
    when(processIndex.getAlias()).thenReturn("index_alias");

    // given and then
    final TasklistRuntimeException thrown =
        assertThatExceptionOfType(TasklistRuntimeException.class)
            .isThrownBy(() -> processStore.getProcess(processId))
            .actual();
    assertThat(thrown.getMessage().contains(errorMessage)).isTrue();
  }

  // ** Test get processes && And get processes with search condition ** //
  @Test
  @SuppressWarnings("unchecked")
  public void shouldNotReturnProcessesWhenResourceAuthIsEnabledButNoAuthorization()
      throws IOException {
    // when
    when(securityConfiguration.getAuthorizations())
        .thenReturn(mock(AuthorizationsConfiguration.class));
    when(securityConfiguration.getAuthorizations().isEnabled()).thenReturn(true);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.user("foo")));
    when(identityProperties.baseUrl()).thenReturn("baseUrl");
    mockAuthenticationOverIdentity(false);
    when(processIndex.getAlias()).thenReturn("index_alias");

    final SearchResponse<ProcessEntity> searchResponse = mock(SearchResponse.class);
    when(es8Client.search(any(SearchRequest.class), eq(ProcessEntity.class)))
        .thenReturn(searchResponse);

    // Level 1: bpmnProcessId_tenantId_buckets -> sterms() with empty buckets
    final var bpmnProcessIdTermsAggregate =
        mock(co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate.class);
    final var aggregate = mock(Aggregate.class);
    when(aggregate.sterms()).thenReturn(bpmnProcessIdTermsAggregate);
    when(searchResponse.aggregations())
        .thenReturn(Map.of("bpmnProcessId_tenantId_buckets", aggregate));

    final Buckets<StringTermsBucket> buckets = mock(Buckets.class);
    when(bpmnProcessIdTermsAggregate.buckets()).thenReturn(buckets);
    when(buckets.array()).thenReturn(Collections.emptyList());

    final List<String> authorizations =
        permissionServices.getProcessDefinitionsWithCreateProcessInstancePermission();

    // given
    final List<ProcessEntity> processes =
        processStore.getProcesses(authorizations, DEFAULT_TENANT_IDENTIFIER, null);
    final List<ProcessEntity> processesWithCondition =
        processStore.getProcesses("*", authorizations, DEFAULT_TENANT_IDENTIFIER, null);

    // then
    assertThat(processes).isEmpty();
    assertThat(processesWithCondition).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldReturnProcessesWhenResourceAuthIsEnabledWithAuthorization() throws Exception {
    // when
    when(securityConfiguration.getAuthorizations())
        .thenReturn(mock(AuthorizationsConfiguration.class));
    mockAuthenticationOverIdentity(true);
    mockElasticSearchSuccessWithAggregatedResponse();
    final List<String> authorizations =
        permissionServices.getProcessDefinitionsWithCreateProcessInstancePermission();

    // given
    final List<ProcessEntity> processes =
        processStore.getProcesses(authorizations, DEFAULT_TENANT_IDENTIFIER, null);
    final List<ProcessEntity> processesWithCondition =
        processStore.getProcesses("*", authorizations, DEFAULT_TENANT_IDENTIFIER, null);

    // then
    assertThat(processes).isNotNull();
    assertThat(processesWithCondition).isNotNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldReturnProcessesWhenResourceAuthorizationIsFalse() throws Exception {
    // when
    when(securityConfiguration.getAuthorizations())
        .thenReturn(mock(AuthorizationsConfiguration.class));
    final CamundaAuthentication camundaAuthentication = CamundaAuthentication.anonymous();
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(camundaAuthentication);
    mockElasticSearchSuccessWithAggregatedResponse();
    when(resourceAccessProvider.resolveResourceAccess(eq(camundaAuthentication), any()))
        .thenAnswer(
            i ->
                ResourceAccess.wildcard(
                    Authorization.withAuthorization(i.getArgument(1), WILDCARD.getResourceId())));

    final List<String> authorizations =
        permissionServices.getProcessDefinitionsWithCreateProcessInstancePermission();

    // given
    final List<ProcessEntity> processes =
        processStore.getProcesses(authorizations, DEFAULT_TENANT_IDENTIFIER, null);
    final List<ProcessEntity> processesWithCondition =
        processStore.getProcesses("*", authorizations, DEFAULT_TENANT_IDENTIFIER, null);

    // then
    assertThat(processes).isNotNull();
    assertThat(processesWithCondition).isNotNull();
  }

  private void mockAuthenticationOverIdentity(final Boolean isAuthorized) {
    // Mock IdentityProperties
    springContextHolder.setApplicationContext(mock(ConfigurableApplicationContext.class));
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.user("foo")));

    if (isAuthorized) {
      when(resourceAccessProvider.resolveResourceAccess(any(), any()))
          .thenReturn(ResourceAccess.wildcard(Authorization.of(b -> b.resourceIds(List.of("*")))));
    } else {
      when(resourceAccessProvider.resolveResourceAccess(any(), any()))
          .thenReturn(ResourceAccess.wildcard(Authorization.of(b -> b.resourceIds(List.of()))));
    }
  }

  @SuppressWarnings("unchecked")
  private void mockElasticSearchSuccess() throws IOException {
    final SearchResponse<ProcessEntity> searchResponse = mock(SearchResponse.class);
    final HitsMetadata<ProcessEntity> hitsMetadata = mock(HitsMetadata.class);
    final ProcessEntity processEntityMock = mock(ProcessEntity.class);
    final TotalHits totalHits = mock(TotalHits.class);

    when(totalHits.value()).thenReturn(1L);
    when(totalHits.relation()).thenReturn(TotalHitsRelation.Eq);
    when(hitsMetadata.total()).thenReturn(totalHits);

    final Hit<ProcessEntity> hit = mock(Hit.class);
    when(hit.source()).thenReturn(processEntityMock);
    when(hitsMetadata.hits()).thenReturn(List.of(hit));
    when(searchResponse.hits()).thenReturn(hitsMetadata);
    when(processEntityMock.getId()).thenReturn("1");
    when(es8Client.search(any(SearchRequest.class), eq(ProcessEntity.class)))
        .thenReturn(searchResponse);
    when(processIndex.getAlias()).thenReturn("index_alias");
  }

  @SuppressWarnings("unchecked")
  private void mockElasticSearchSuccessWithAggregatedResponse() throws IOException {
    final SearchResponse<ProcessEntity> mockedSearchResponse = mock(SearchResponse.class);
    when(es8Client.search(any(SearchRequest.class), eq(ProcessEntity.class)))
        .thenReturn(mockedSearchResponse);
    when(processIndex.getAlias()).thenReturn("index_alias");

    // Level 1: bpmnProcessId_tenantId_buckets -> sterms()
    final var bpmnProcessIdTermsAggregate =
        mock(co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate.class);
    final var bpmnProcessIdAggregate = mock(Aggregate.class);
    when(bpmnProcessIdAggregate.sterms()).thenReturn(bpmnProcessIdTermsAggregate);
    when(mockedSearchResponse.aggregations())
        .thenReturn(Map.of("bpmnProcessId_tenantId_buckets", bpmnProcessIdAggregate));

    final Buckets<StringTermsBucket> bpmnProcessIdBuckets = mock(Buckets.class);
    when(bpmnProcessIdTermsAggregate.buckets()).thenReturn(bpmnProcessIdBuckets);

    final StringTermsBucket bpmnProcessIdBucket = mock(StringTermsBucket.class);
    when(bpmnProcessIdBuckets.array()).thenReturn(List.of(bpmnProcessIdBucket));

    // Level 2: group_by_tenant_id -> sterms()
    final var tenantIdTermsAggregate =
        mock(co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate.class);
    final var tenantIdAggregate = mock(Aggregate.class);
    when(tenantIdAggregate.sterms()).thenReturn(tenantIdTermsAggregate);
    when(bpmnProcessIdBucket.aggregations())
        .thenReturn(Map.of("group_by_tenant_id", tenantIdAggregate));

    final Buckets<StringTermsBucket> tenantIdBuckets = mock(Buckets.class);
    when(tenantIdTermsAggregate.buckets()).thenReturn(tenantIdBuckets);

    final StringTermsBucket tenantIdBucket = mock(StringTermsBucket.class);
    when(tenantIdBuckets.array()).thenReturn(List.of(tenantIdBucket));

    // Level 3: max_version_docs -> lterms()
    final var maxVersionTermsAggregate =
        mock(co.elastic.clients.elasticsearch._types.aggregations.LongTermsAggregate.class);
    final var maxVersionAggregate = mock(Aggregate.class);
    when(maxVersionAggregate.lterms()).thenReturn(maxVersionTermsAggregate);
    when(tenantIdBucket.aggregations()).thenReturn(Map.of("max_version_docs", maxVersionAggregate));

    final Buckets<LongTermsBucket> maxVersionBuckets = mock(Buckets.class);
    when(maxVersionTermsAggregate.buckets()).thenReturn(maxVersionBuckets);

    final LongTermsBucket versionBucket = mock(LongTermsBucket.class);
    when(maxVersionBuckets.array()).thenReturn(List.of(versionBucket));

    // Level 4: top_hit_doc -> topHits()
    final TopHitsAggregate topHitsAggregate = mock(TopHitsAggregate.class);
    final var topHitsAgg = mock(Aggregate.class);
    when(topHitsAgg.topHits()).thenReturn(topHitsAggregate);
    when(versionBucket.aggregations()).thenReturn(Map.of("top_hit_doc", topHitsAgg));

    final HitsMetadata<co.elastic.clients.json.JsonData> topHitsMetadata = mock(HitsMetadata.class);
    when(topHitsAggregate.hits()).thenReturn(topHitsMetadata);

    final Hit<co.elastic.clients.json.JsonData> topHit = mock(Hit.class);
    when(topHitsMetadata.hits()).thenReturn(List.of(topHit));

    final co.elastic.clients.json.JsonData jsonData = mock(co.elastic.clients.json.JsonData.class);
    when(topHit.source()).thenReturn(jsonData);
    when(jsonData.to(ProcessEntity.class)).thenReturn(new ProcessEntity());
  }

  @SuppressWarnings("unchecked")
  private void mockElasticSearchNotFound() throws IOException {
    final SearchResponse<ProcessEntity> searchResponse = mock(SearchResponse.class);
    final HitsMetadata<ProcessEntity> hitsMetadata = mock(HitsMetadata.class);
    final TotalHits totalHits = mock(TotalHits.class);

    when(totalHits.value()).thenReturn(0L);
    when(totalHits.relation()).thenReturn(TotalHitsRelation.Eq);
    when(hitsMetadata.total()).thenReturn(totalHits);
    when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
    when(searchResponse.hits()).thenReturn(hitsMetadata);
    when(es8Client.search(any(SearchRequest.class), eq(ProcessEntity.class)))
        .thenReturn(searchResponse);
    when(processIndex.getAlias()).thenReturn("index_alias");
  }
}
