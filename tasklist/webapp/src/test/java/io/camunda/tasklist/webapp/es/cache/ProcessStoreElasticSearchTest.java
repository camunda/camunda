/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.cache;

import static io.camunda.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.AuthorizationsConfiguration;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.elasticsearch.ProcessStoreElasticSearch;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.permission.TasklistPermissionServices;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import org.elasticsearch.search.aggregations.metrics.TopHits;
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
  @Mock private TenantAwareElasticsearchClient tenantAwareClient;
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
        permissionServices, "securityConfiguration", securityConfiguration);
    ReflectionTestUtils.setField(
        permissionServices, "authenticationProvider", authenticationProvider);
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
    when(tenantAwareClient.search(any(SearchRequest.class))).thenThrow(mockedException);
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

    when(tenantAwareClient.search(any(SearchRequest.class))).thenThrow(exception);
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
  public void shouldNotReturnProcessesWhenResourceAuthIsEnabledButNoAuthorization()
      throws IOException {
    // when
    when(securityConfiguration.getAuthorizations())
        .thenReturn(mock(AuthorizationsConfiguration.class));
    when(securityConfiguration.getAuthorizations().isEnabled()).thenReturn(true);
    when(identityProperties.baseUrl()).thenReturn("baseUrl");
    mockAuthenticationOverIdentity(false);
    when(processIndex.getAlias()).thenReturn("index_alias");
    final SearchResponse searchResponse = mock(SearchResponse.class);
    when(tenantAwareClient.search(any(SearchRequest.class))).thenReturn(searchResponse);
    final var aggregations = mock(Aggregations.class);
    when(searchResponse.getAggregations()).thenReturn(aggregations);
    final var compositeAggregation = mock(CompositeAggregation.class);
    when(aggregations.get("bpmnProcessId_tenantId_buckets")).thenReturn(compositeAggregation);
    when(compositeAggregation.getBuckets()).thenReturn(Collections.emptyList());
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
  public void shouldReturnProcessesWhenResourceAuthIsEnabledWithAuthorization() throws Exception {
    // when
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
  public void shouldReturnProcessesWhenResourceAuthorizationIsFalse() throws Exception {
    // when
    when(securityConfiguration.getAuthorizations())
        .thenReturn(mock(AuthorizationsConfiguration.class));
    when(securityConfiguration.getAuthorizations().isEnabled()).thenReturn(false);
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

  private void mockAuthenticationOverIdentity(final Boolean isAuthorized) {
    // Mock IdentityProperties
    springContextHolder.setApplicationContext(mock(ConfigurableApplicationContext.class));
    when(securityConfiguration.getAuthorizations())
        .thenReturn(mock(AuthorizationsConfiguration.class));
    when(securityConfiguration.getAuthorizations().isEnabled()).thenReturn(true);

    if (isAuthorized) {
      when(resourceAccessProvider.resolveResourceAccess(any(), any()))
          .thenReturn(ResourceAccess.wildcard(Authorization.of(b -> b.resourceIds(List.of("*")))));
    } else {
      when(resourceAccessProvider.resolveResourceAccess(any(), any()))
          .thenReturn(ResourceAccess.wildcard(Authorization.of(b -> b.resourceIds(List.of()))));
    }
  }

  private void mockElasticSearchSuccess() throws IOException {
    final SearchResponse searchResponse = mock(SearchResponse.class);
    final SearchHits searchHits = mock(SearchHits.class);
    final ProcessEntity processEntityMock = mock(ProcessEntity.class);
    final TotalHits totalHits = new TotalHits(1L, TotalHits.Relation.EQUAL_TO);
    final String jsonString = "any-json-string";

    when(searchHits.getTotalHits()).thenReturn(totalHits);
    when(searchHits.getHits()).thenReturn(new SearchHit[] {mock(SearchHit.class)});
    when(searchHits.getHits()[0].getSourceAsString()).thenReturn(jsonString);
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(processEntityMock.getId()).thenReturn("1");
    when(ElasticsearchUtil.fromSearchHit(jsonString, objectMapper, ProcessEntity.class))
        .thenReturn(processEntityMock);
    when(tenantAwareClient.search(any(SearchRequest.class))).thenReturn(searchResponse);
    when(processIndex.getAlias()).thenReturn("index_alias");
  }

  private void mockElasticSearchSuccessWithAggregatedResponse() throws IOException {
    final var mockedSearchResponse = mock(SearchResponse.class);
    when(tenantAwareClient.search(any(SearchRequest.class))).thenReturn(mockedSearchResponse);
    when(processIndex.getAlias()).thenReturn("index_alias");

    final var aggregations = mock(Aggregations.class);
    when(mockedSearchResponse.getAggregations()).thenReturn(aggregations);
    final var compositeAggregation = mock(CompositeAggregation.class);
    when(aggregations.get("bpmnProcessId_tenantId_buckets")).thenReturn(compositeAggregation);
    final var bucket = mock(CompositeAggregation.Bucket.class);
    when((List<CompositeAggregation.Bucket>) compositeAggregation.getBuckets())
        .thenReturn(List.of(bucket));
    when(bucket.getAggregations()).thenReturn(aggregations);
    final var termsBucket = mock(ParsedTerms.ParsedBucket.class);
    final var maxVersionTerms = mock(ParsedTerms.class);
    when((List<ParsedTerms.ParsedBucket>) maxVersionTerms.getBuckets())
        .thenReturn(List.of(termsBucket));
    when(aggregations.get("max_version_docs")).thenReturn(maxVersionTerms);
    final var versionBucket = mock(ParsedTerms.ParsedBucket.class);
    when((List<ParsedTerms.ParsedBucket>) maxVersionTerms.getBuckets())
        .thenReturn(List.of(versionBucket));
    when(versionBucket.getAggregations()).thenReturn(aggregations);
    final var topHits = mock(TopHits.class);
    when(aggregations.get("top_hit_doc")).thenReturn(topHits);
    final var searchHits = mock(SearchHits.class);
    when(topHits.getHits()).thenReturn(searchHits);
    final var searchHit = mock(SearchHit.class);
    when(searchHits.getHits()).thenReturn(new SearchHit[] {searchHit});
    when(searchHit.getSourceAsString()).thenReturn("");
  }

  private void mockElasticSearchNotFound() throws IOException {
    final SearchResponse searchResponse = mock(SearchResponse.class);
    final SearchHits searchHits = mock(SearchHits.class);
    final TotalHits totalHits = new TotalHits(0L, TotalHits.Relation.EQUAL_TO);

    when(searchResponse.getHits()).thenReturn(searchHits);
    when(searchHits.getTotalHits()).thenReturn(totalHits);
    when(searchHits.getHits()).thenReturn(new SearchHit[] {mock(SearchHit.class)});
    when(tenantAwareClient.search(any(SearchRequest.class))).thenReturn(searchResponse);
    when(processIndex.getAlias()).thenReturn("index_alias");
  }
}
