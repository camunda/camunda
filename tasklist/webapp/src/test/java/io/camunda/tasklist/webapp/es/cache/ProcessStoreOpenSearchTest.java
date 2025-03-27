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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.authentication.entity.CamundaUser;
import io.camunda.security.configuration.AuthorizationsConfiguration;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.opensearch.ProcessStoreOpenSearch;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.permission.TasklistPermissionServices;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
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
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.CompositeBucket;
import org.opensearch.client.opensearch._types.aggregations.LongTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.LongTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.TopHitsAggregate;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessStoreOpenSearchTest {
  @Mock private ProcessIndex processIndex;
  @Mock private TenantAwareOpenSearchClient tenantAwareClient;
  @InjectMocks private ProcessStoreOpenSearch processStore;
  @Mock private ObjectMapper objectMapper;
  @InjectMocks private SpringContextHolder springContextHolder;
  @Mock private SecurityConfiguration securityConfiguration;
  @Mock private TasklistProperties tasklistProperties;
  @Mock private io.camunda.identity.autoconfigure.IdentityProperties identityProperties;
  @Mock private SecurityContextProvider securityContextProvider;
  @Mock private AuthorizationChecker authorizationChecker;
  @Mock private TasklistPermissionServices permissionServices;

  @BeforeEach
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(securityConfiguration.getMultiTenancy()).thenReturn(new MultiTenancyConfiguration());
    ReflectionTestUtils.setField(
        permissionServices, "securityConfiguration", securityConfiguration);
  }

  // ** Test Get Process by BPMN Process Id ** //
  @Test
  public void shouldReturnAProcessEntityWhenGetProcessByBpmnIdIsCalled() throws IOException {
    // given
    mockOpenSearchSuccess();

    // when
    final ProcessEntity result = processStore.getProcessByBpmnProcessId("bpmnProcessId");

    // then
    assertNotNull(result);
    assertEquals("1", result.getId());
  }

  @Test
  public void shouldReturnNotFoundWhenESReturnsZeroHits() throws IOException {
    // given
    mockOpenSearchNotFound();

    // when and then
    assertThrows(
        NotFoundException.class,
        () -> processStore.getProcessByBpmnProcessId("bpmnProcessId_not_exist"));
  }

  @Test
  public void shouldReturnIOExceptionForGetProcessByBpmnId() throws IOException {
    // given
    final IOException mockedException = new IOException("IO Exception during search");
    when(tenantAwareClient.search(any(), eq(ProcessEntity.class))).thenThrow(mockedException);
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
    mockOpenSearchSuccess();

    // given
    final ProcessEntity result = processStore.getProcess("1");

    // then
    assertNotNull(result);
  }

  @Test
  public void shouldGetProcessReturnNotFound() throws IOException {
    // when
    mockOpenSearchNotFound();

    // given and then
    assertThrows(TasklistRuntimeException.class, () -> processStore.getProcess("processId"));
  }

  @Test
  public void shouldGetProcessReturnExceptionThrown() throws IOException {
    // when
    final String processId = "processId";
    final String errorMessage = "IOException error message";
    final IOException exception = new IOException(errorMessage);

    when(tenantAwareClient.search(any(), eq(ProcessEntity.class))).thenThrow(exception);
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
    when(securityConfiguration.getAuthorizations())
        .thenReturn(mock(AuthorizationsConfiguration.class));
    when(securityConfiguration.getAuthorizations().isEnabled()).thenReturn(true);
    when(identityProperties.baseUrl()).thenReturn("baseUrl");
    mockAuthenticationOverIdentity(false);
    when(processIndex.getAlias()).thenReturn("index_alias");
    final SearchResponse<ProcessEntity> searchResponse = mock(SearchResponse.class);
    when(tenantAwareClient.search(any(), eq(ProcessEntity.class))).thenReturn(searchResponse);

    final var aggregations = mock(Aggregate.class, RETURNS_DEEP_STUBS);
    when(searchResponse.aggregations())
        .thenReturn(Map.of("bpmnProcessId_tenantId_buckets", aggregations));
    final var bucket = mock(CompositeBucket.class);
    when(aggregations.composite().buckets().array()).thenReturn(Collections.emptyList());
    final var topHits = mock(TopHitsAggregate.class, RETURNS_DEEP_STUBS);
    when(OpenSearchUtil.mapSearchHits(topHits.hits().hits(), objectMapper, ProcessEntity.class))
        .thenReturn(List.of(mock(ProcessEntity.class)));
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
    mockOpenSearchSuccessWithAggregatedResponse();
    final List<String> authorizations =
        permissionServices.getProcessDefinitionsWithCreateProcessInstancePermission();

    // given
    final List<ProcessEntity> processes =
        processStore.getProcesses(authorizations, DEFAULT_TENANT_IDENTIFIER, null);
    final List<ProcessEntity> processesWithCondition =
        processStore.getProcesses("*", authorizations, DEFAULT_TENANT_IDENTIFIER, null);

    // then
    assertNotNull(processes);
    assertNotNull(processesWithCondition);
  }

  @Test
  public void shouldReturnProcessesWhenResourceAuthorizationIsFalse() throws Exception {
    // when
    when(securityConfiguration.getAuthorizations())
        .thenReturn(mock(AuthorizationsConfiguration.class));
    when(securityConfiguration.getAuthorizations().isEnabled()).thenReturn(false);
    mockOpenSearchSuccessWithAggregatedResponse();

    final List<String> authorizations =
        permissionServices.getProcessDefinitionsWithCreateProcessInstancePermission();

    // given
    final List<ProcessEntity> processes =
        processStore.getProcesses(authorizations, DEFAULT_TENANT_IDENTIFIER, null);
    final List<ProcessEntity> processesWithCondition =
        processStore.getProcesses("*", authorizations, DEFAULT_TENANT_IDENTIFIER, null);

    // then
    assertNotNull(processes);
    assertNotNull(processesWithCondition);
  }

  private void mockAuthenticationOverIdentity(final Boolean isAuthorizated) {
    // Mock IdentityProperties
    springContextHolder.setApplicationContext(mock(ConfigurableApplicationContext.class));
    when(securityConfiguration.getAuthorizations())
        .thenReturn(mock(AuthorizationsConfiguration.class));
    when(securityConfiguration.getAuthorizations().isEnabled()).thenReturn(true);

    // Mock Authentication
    final org.springframework.security.core.Authentication auth = mock(Authentication.class);
    final CamundaUser camundaUser = mock(CamundaUser.class);
    when(auth.getPrincipal()).thenReturn(camundaUser);
    when(camundaUser.getUserKey()).thenReturn(123L);

    // Mock SecurityContextHolder
    final SecurityContext securityContext = mock(SecurityContext.class);
    SecurityContextHolder.getContext().setAuthentication(auth);
    when(securityContext.getAuthentication()).thenReturn(auth);

    when(securityContextProvider.provideSecurityContext(any()))
        .thenReturn(mock(io.camunda.security.auth.SecurityContext.class));

    if (isAuthorizated) {
      when(authorizationChecker.retrieveAuthorizedResourceKeys(any())).thenReturn(List.of("*"));
    } else {
      when(authorizationChecker.retrieveAuthorizedResourceKeys(any())).thenReturn(List.of());
    }
  }

  private void mockOpenSearchSuccess() throws IOException {
    final SearchResponse searchResponse = mock(SearchResponse.class, RETURNS_DEEP_STUBS);
    final ProcessEntity processEntityMock = mock(ProcessEntity.class);
    final Hit hit = mock(Hit.class);
    when(searchResponse.hits().total().value()).thenReturn(1L);
    when(searchResponse.hits().hits()).thenReturn(List.of(hit));
    when(hit.source()).thenReturn(processEntityMock);
    when(processEntityMock.getId()).thenReturn("1");
    when(tenantAwareClient.search(any(), eq(ProcessEntity.class))).thenReturn(searchResponse);
    when(processIndex.getAlias()).thenReturn("index_alias");
  }

  private void mockOpenSearchSuccessWithAggregatedResponse() throws IOException {
    final var mockedSearchResponse = mock(SearchResponse.class);
    when(tenantAwareClient.search(any(), eq(ProcessEntity.class))).thenReturn(mockedSearchResponse);
    when(processIndex.getAlias()).thenReturn("index_alias");

    final var aggregations = mock(Aggregate.class, RETURNS_DEEP_STUBS);
    when(mockedSearchResponse.aggregations())
        .thenReturn(Map.of("bpmnProcessId_tenantId_buckets", aggregations));
    final var bucket = mock(CompositeBucket.class);
    when(aggregations.composite().buckets().array()).thenReturn(List.of(bucket));

    final var maxVersionAggregate = mock(Aggregate.class, RETURNS_DEEP_STUBS);
    when(bucket.aggregations()).thenReturn(Map.of("max_version_docs", maxVersionAggregate));
    final var termsAggregate = mock(LongTermsAggregate.class);
    when(maxVersionAggregate._get()).thenReturn(termsAggregate);
    final var termsBucket = mock(Buckets.class);
    when(termsAggregate.buckets()).thenReturn(termsBucket);
    final var longTermsBucket = mock(LongTermsBucket.class);
    when(termsBucket.array()).thenReturn(List.of(longTermsBucket));
    final var topHitsAggregate = mock(Aggregate.class);
    when(longTermsBucket.aggregations()).thenReturn(Map.of("top_hit_doc", topHitsAggregate));
    final var topHits = mock(TopHitsAggregate.class, RETURNS_DEEP_STUBS);
    when(topHitsAggregate._get()).thenReturn(topHits);
    final Hit hit = mock(Hit.class);
    when(topHits.hits().hits()).thenReturn(List.of(hit));
    when(hit.source()).thenReturn("some-json");
    when(objectMapper.readValue("some-json", ProcessEntity.class))
        .thenReturn(mock(ProcessEntity.class));
  }

  private void mockOpenSearchNotFound() throws IOException {
    final SearchResponse searchResponse = mock(SearchResponse.class, RETURNS_DEEP_STUBS);
    final Hit hit = mock(Hit.class);
    when(searchResponse.hits().hits()).thenReturn(Collections.emptyList());
    when(tenantAwareClient.search(any(), eq(ProcessEntity.class))).thenReturn(searchResponse);
    when(processIndex.getAlias()).thenReturn("index_alias");
  }
}
