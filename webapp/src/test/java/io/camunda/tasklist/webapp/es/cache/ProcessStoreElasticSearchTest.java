/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es.cache;

import static io.camunda.zeebe.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.property.MultiTenancyProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.store.elasticsearch.ProcessStoreElasticSearch;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthentication;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorization;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorizationService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessStoreElasticSearchTest {
  @Mock private ProcessIndex processIndex;
  @Mock private TenantAwareElasticsearchClient tenantAwareClient;
  @InjectMocks private ProcessStoreElasticSearch processStore;
  @InjectMocks private IdentityAuthorizationService identityService;
  @Mock private ObjectMapper objectMapper;
  @InjectMocks private SpringContextHolder springContextHolder;
  @Mock private TasklistProperties tasklistProperties;

  @BeforeEach
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(tasklistProperties.getMultiTenancy()).thenReturn(new MultiTenancyProperties());
  }

  // ** Test Get Process by BPMN Process Id ** //
  @Test
  public void shouldReturnAProcessEntityWhenGetProcessByBpmnIdIsCalled() throws IOException {
    // given
    mockElasticSearchSuccess();

    // when
    final ProcessEntity result = processStore.getProcessByBpmnProcessId("bpmnProcessId");

    // then
    assertNotNull(result);
    assertEquals("1", result.getId());
  }

  @Test
  public void shouldReturnNotFoundWhenESReturnsZeroHits() throws IOException {
    // given
    mockElasticSearchNotFound();

    // when and then
    assertThrows(
        NotFoundException.class,
        () -> processStore.getProcessByBpmnProcessId("bpmnProcessId_not_exist"));
  }

  @Test
  public void shouldReturnIOExceptionForGetProcessByBpmnId() throws IOException {
    // given
    final IOException mockedException = new IOException("IO Exception during search");
    when(tenantAwareClient.search(any(SearchRequest.class))).thenThrow(mockedException);
    when(processIndex.getAlias()).thenReturn("alias");

    // when
    final TasklistRuntimeException thrown =
        assertThrows(
            TasklistRuntimeException.class,
            () -> processStore.getProcessByBpmnProcessId("bpmnProcessId"));

    // then
    assertTrue(
        thrown
            .getMessage()
            .contains(
                "Exception occurred, while obtaining the process: "
                    + mockedException.getMessage()));
  }

  // ** Test Get Process by Process Id ** //
  @Test
  public void shouldGetProcessReturnAProcessById() throws IOException {
    // when
    mockElasticSearchSuccess();

    // given
    final ProcessEntity result = processStore.getProcess("1");

    // then
    assertNotNull(result);
  }

  @Test
  public void shouldGetProcessReturnNotFound() throws IOException {
    // when
    mockElasticSearchNotFound();

    // given and then
    assertThrows(TasklistRuntimeException.class, () -> processStore.getProcess("processId"));
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
        assertThrows(TasklistRuntimeException.class, () -> processStore.getProcess(processId));
    assertTrue(thrown.getMessage().contains(errorMessage));
  }

  // ** Test get processes && And get processes with search condition ** //
  @Test
  public void shouldNotReturnProcessesWhenResourceAuthIsEnabledButNoAuthorization()
      throws IOException {
    // when
    when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
    when(tasklistProperties.getIdentity().isResourcePermissionsEnabled()).thenReturn(true);
    when(tasklistProperties.getIdentity().getBaseUrl()).thenReturn("baseUrl");
    mockAuthenticationOverIdentity(false);
    when(processIndex.getAlias()).thenReturn("index_alias");
    final SearchResponse searchResponse = mock(SearchResponse.class);
    when(tenantAwareClient.search(any(SearchRequest.class))).thenReturn(searchResponse);
    final var aggregations = mock(Aggregations.class);
    when(searchResponse.getAggregations()).thenReturn(aggregations);
    final var definitionIdTerms = mock(Terms.class);
    when(aggregations.get("group_by_definition_id")).thenReturn(definitionIdTerms);
    when(definitionIdTerms.getBuckets()).thenReturn(Collections.emptyList());
    final List<String> authorizations = identityService.getProcessDefinitionsFromAuthorization();

    // given
    final List<ProcessEntity> processes =
        processStore.getProcesses(authorizations, DEFAULT_TENANT_IDENTIFIER);
    final List<ProcessEntity> processesWithCondition =
        processStore.getProcesses("*", authorizations, DEFAULT_TENANT_IDENTIFIER);

    // then
    assertThat(processes).isEmpty();
    assertThat(processesWithCondition).isEmpty();
  }

  @Test
  public void shouldReturnProcessesWhenResourceAuthIsEnabledWithAuthorization() throws Exception {
    // when
    mockAuthenticationOverIdentity(true);
    mockElasticSearchSuccessWithAggregatedResponse();
    final List<String> authorizations = identityService.getProcessDefinitionsFromAuthorization();

    // given
    final List<ProcessEntity> processes =
        processStore.getProcesses(authorizations, DEFAULT_TENANT_IDENTIFIER);
    final List<ProcessEntity> processesWithCondition =
        processStore.getProcesses("*", authorizations, DEFAULT_TENANT_IDENTIFIER);

    // then
    assertNotNull(processes);
    assertNotNull(processesWithCondition);
  }

  @Test
  public void shouldReturnProcessesWhenResourceAuthorizationIsFalse() throws Exception {
    // when
    when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
    when(tasklistProperties.getIdentity().isResourcePermissionsEnabled()).thenReturn(false);
    mockElasticSearchSuccessWithAggregatedResponse();

    final List<String> authorizations = identityService.getProcessDefinitionsFromAuthorization();

    // given
    final List<ProcessEntity> processes =
        processStore.getProcesses(authorizations, DEFAULT_TENANT_IDENTIFIER);
    final List<ProcessEntity> processesWithCondition =
        processStore.getProcesses("*", authorizations, DEFAULT_TENANT_IDENTIFIER);

    // then
    assertNotNull(processes);
    assertNotNull(processesWithCondition);
  }

  private void mockAuthenticationOverIdentity(Boolean isAuthorizated) {
    // Mock IdentityProperties
    final IdentityProperties identityProperties = mock(IdentityProperties.class);
    springContextHolder.setApplicationContext(mock(ConfigurableApplicationContext.class));

    // Define behavior of IdentityProperties methods
    when(identityProperties.isResourcePermissionsEnabled()).thenReturn(true);
    when(identityProperties.getBaseUrl()).thenReturn("baseUrl");

    // Define behavior of tasklistProperties.getIdentity()
    when(tasklistProperties.getIdentity()).thenReturn(identityProperties);

    // Mock Authentication
    final Authentication auth = mock(Authentication.class);

    // Mock IdentityAuthentication
    final IdentityAuthentication identityAuthentication = mock(IdentityAuthentication.class);

    // Mock SpringContextHolder
    final Identity identity = mock(Identity.class);
    when(SpringContextHolder.getBean(Identity.class)).thenReturn(identity);
    when(identity.authentication()).thenReturn(auth);

    // Mock SecurityContextHolder
    final SecurityContext securityContext = mock(SecurityContext.class);
    SecurityContextHolder.getContext().setAuthentication(identityAuthentication);
    when(securityContext.getAuthentication()).thenReturn(identityAuthentication);

    when(identityAuthentication.getAuthorizations()).thenReturn(mock(IdentityAuthorization.class));
    if (isAuthorizated) {
      when(identityAuthentication.getAuthorizations().getProcessesAllowedToStart())
          .thenReturn(List.of("*"));
    } else {
      when(identityAuthentication.getAuthorizations().getProcessesAllowedToStart())
          .thenReturn(new ArrayList<>());
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
    final var definitionIdTerms = mock(Terms.class);
    when(aggregations.get("group_by_definition_id")).thenReturn(definitionIdTerms);
    final var definitionIdBucket = mock(ParsedStringTerms.ParsedBucket.class);
    when((List<ParsedStringTerms.ParsedBucket>) definitionIdTerms.getBuckets())
        .thenReturn(List.of(definitionIdBucket));
    final var definitionIdBucketAggregations = mock(Aggregations.class);
    when(definitionIdBucket.getAggregations()).thenReturn(definitionIdBucketAggregations);
    final var tenantIdTerms = mock(Terms.class);
    when(definitionIdBucketAggregations.get("group_by_tenant_id")).thenReturn(tenantIdTerms);
    final var tenantIdBucket = mock(ParsedStringTerms.ParsedBucket.class);
    when((List<ParsedStringTerms.ParsedBucket>) tenantIdTerms.getBuckets())
        .thenReturn(List.of(tenantIdBucket));
    final var tenantIdBucketAggregations = mock(Aggregations.class);
    final var topHits = mock(TopHits.class);
    when(tenantIdBucket.getAggregations()).thenReturn(tenantIdBucketAggregations);
    when(tenantIdBucketAggregations.get("top_hit_doc")).thenReturn(topHits);
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
