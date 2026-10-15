/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.MappingRuleSearchClient;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.MappingRuleServices.MappingRuleDTO;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingRuleCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingRuleDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingRuleUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.value.MappingRuleType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class MappingRuleServicesTest {

  private static final String PHYSICAL_TENANT_ID = "test-tenant";
  ArgumentCaptor<BrokerMappingRuleDeleteRequest> mappingRuleDeleteRequestArgumentCaptor;
  ArgumentCaptor<BrokerMappingRuleUpdateRequest> mappingRuleUpdateRequestArgumentCaptor;
  private MappingRuleServices services;
  private MappingRuleSearchClient client;
  private CamundaAuthentication authentication;
  private StubbedBrokerClient stubbedBrokerClient;
  private SearchQueryResult<MappingRuleEntity> result;
  private ApiServicesExecutorProvider executorProvider;
  private BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter;

  @BeforeEach
  public void before() {
    authentication = CamundaAuthentication.of(builder -> builder.user("foo"));
    stubbedBrokerClient = new StubbedBrokerClient();
    client = mock(MappingRuleSearchClient.class);
    result = mock(SearchQueryResult.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    when(client.searchMappingRules(any())).thenReturn(result);
    mappingRuleDeleteRequestArgumentCaptor =
        ArgumentCaptor.forClass(BrokerMappingRuleDeleteRequest.class);
    mappingRuleUpdateRequestArgumentCaptor =
        ArgumentCaptor.forClass(BrokerMappingRuleUpdateRequest.class);
    executorProvider = mock(ApiServicesExecutorProvider.class);
    when(executorProvider.getExecutor()).thenReturn(ForkJoinPool.commonPool());
    brokerRequestAuthorizationConverter = mock(BrokerRequestAuthorizationConverter.class);
    services =
        new MappingRuleServices(
            PHYSICAL_TENANT_ID,
            stubbedBrokerClient,
            mock(SecurityContextProvider.class),
            client,
            executorProvider,
            brokerRequestAuthorizationConverter);
  }

  @Test
  public void shouldCreateMappingRule() {
    // given
    final var mappingRuleDTO =
        new MappingRuleDTO("newClaimName", "newClaimValue", "mappingRuleName", "mappingRuleId");

    // when
    services.createMappingRule(mappingRuleDTO, authentication);

    // then
    final BrokerMappingRuleCreateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(MappingRuleIntent.CREATE);
    assertThat(request.getValueType()).isEqualTo(ValueType.MAPPING_RULE);
    final MappingRuleRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getClaimName()).isEqualTo(mappingRuleDTO.claimName());
    assertThat(brokerRequestValue.getClaimValue()).isEqualTo(mappingRuleDTO.claimValue());
    assertThat(brokerRequestValue.getName()).isEqualTo(mappingRuleDTO.name());
  }

  @Test
  public void shouldEmptyQueryReturnRoles() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchMappingRules(any())).thenReturn(result);

    final MappingRuleFilter filter = new MappingRuleFilter.Builder().build();
    final var searchQuery = SearchQueryBuilders.mappingRuleSearchQuery((b) -> b.filter(filter));

    // when
    final var searchQueryResult = services.search(searchQuery, authentication);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnSingleVariable() {
    // given
    final var entity = mock(MappingRuleEntity.class);
    final var result = new SearchQueryResult<>(1, false, List.of(entity), null, null);
    when(client.searchMappingRules(any())).thenReturn(result);
  }

  @Test
  public void shouldReturnSingleMappingRuleForFind() {
    // given
    final var entity = mock(MappingRuleEntity.class);
    when(client.getMappingRule(any(String.class))).thenReturn(entity);

    // when
    final var searchQueryResult = services.getMappingRule("mappingRuleId", authentication);

    // then
    assertThat(searchQueryResult).isEqualTo(entity);
  }

  @Test
  public void shouldTriggerDeleteRequest() {
    // given
    final CamundaAuthentication testAuthentication = mock(CamundaAuthentication.class);
    when(testAuthentication.claims()).thenReturn(Map.of());
    final BrokerClient mockBrokerClient = mock(BrokerClient.class);
    final MappingRuleServices testMappingRuleServices =
        new MappingRuleServices(
            PHYSICAL_TENANT_ID,
            mockBrokerClient,
            mock(SecurityContextProvider.class),
            client,
            executorProvider,
            brokerRequestAuthorizationConverter);

    final var mappingRuleRecord = new MappingRuleRecord();
    mappingRuleRecord.setMappingRuleId("id");
    when(mockBrokerClient.sendRequest(any()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(mappingRuleRecord)));

    //  when
    testMappingRuleServices.deleteMappingRule("id", testAuthentication);

    // then
    verify(mockBrokerClient).sendRequest(mappingRuleDeleteRequestArgumentCaptor.capture());
    assertThat(mappingRuleDeleteRequestArgumentCaptor.getValue().getPartitionGroup())
        .isEqualTo(PHYSICAL_TENANT_ID);
    final var request = mappingRuleDeleteRequestArgumentCaptor.getValue();
    assertThat(request.getRequestWriter().getMappingRuleId()).isEqualTo("id");
  }

  @Test
  public void shouldTriggerUpdateRequest() {
    // given
    final CamundaAuthentication testAuthentication = mock(CamundaAuthentication.class);
    when(testAuthentication.claims()).thenReturn(Map.of());
    final BrokerClient mockBrokerClient = mock(BrokerClient.class);
    final MappingRuleServices testMappingRuleServices =
        new MappingRuleServices(
            PHYSICAL_TENANT_ID,
            mockBrokerClient,
            mock(SecurityContextProvider.class),
            client,
            executorProvider,
            brokerRequestAuthorizationConverter);

    final var mappingRuleRecord = new MappingRuleRecord();
    mappingRuleRecord.setMappingRuleId("id");
    when(mockBrokerClient.sendRequest(any()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(mappingRuleRecord)));

    final var mappingRuleDTO =
        new MappingRuleDTO(
            "newClaimName",
            "newClaimValue",
            "newMappingRuleName",
            mappingRuleRecord.getMappingRuleId());

    //  when
    testMappingRuleServices.updateMappingRule(mappingRuleDTO, testAuthentication);

    // then
    verify(mockBrokerClient).sendRequest(mappingRuleUpdateRequestArgumentCaptor.capture());
    assertThat(mappingRuleUpdateRequestArgumentCaptor.getValue().getPartitionGroup())
        .isEqualTo(PHYSICAL_TENANT_ID);
    final var request = mappingRuleUpdateRequestArgumentCaptor.getValue();
    assertThat(request.getRequestWriter().getMappingRuleId())
        .isEqualTo(mappingRuleDTO.mappingRuleId());
    assertThat(request.getRequestWriter().getClaimName()).isEqualTo(mappingRuleDTO.claimName());
    assertThat(request.getRequestWriter().getClaimValue()).isEqualTo(mappingRuleDTO.claimValue());
    assertThat(request.getRequestWriter().getName()).isEqualTo(mappingRuleDTO.name());
  }

  @Test
  public void getMatchingMappingRules() {
    // given
    final Map<String, Object> claims =
        Map.of("c1", "v1", "c2", List.of("v2.1", "v2.2"), "c3", 300, "c4", true);
    // when
    services.getMatchingMappingRules(claims, authentication);
    // then
    final ArgumentCaptor<MappingRuleQuery> queryCaptor =
        ArgumentCaptor.forClass(MappingRuleQuery.class);
    verify(client, times(1)).searchMappingRules(queryCaptor.capture());
    final MappingRuleQuery query = queryCaptor.getValue();
    assertThat(query.filter().claimName()).isNull();
    assertThat(query.filter().claimValue()).isNull();
    assertThat(query.filter().nameOperations()).isNull();
    assertThat(query.filter().claims()).isNull();
  }

  @Test
  public void shouldMatchTokenRulesAgainstClaimsAndAttributeRulesAgainstAttributes() {
    // given
    final var tokenRule = new MappingRuleEntity("token", 1L, "$.team", "red", "token");
    final var attributeRule =
        new MappingRuleEntity(
            "attribute", 2L, "$.department", "Finance", "attribute", "USER_ATTRIBUTE");
    final var rules =
        new SearchQueryResult<>(2, false, List.of(tokenRule, attributeRule), null, null);
    when(client.searchMappingRules(any())).thenReturn(rules);

    // when / then
    assertThat(
            services.getMatchingMappingRules(
                Map.of("team", "red"), Map.of("department", "Finance"), authentication))
        .extracting(MappingRuleEntity::mappingRuleId)
        .containsExactlyInAnyOrder("token", "attribute");
  }

  @Test
  public void shouldMatchGroupAttributeRulesAgainstAnyGroupAttributesOnly() {
    // given
    final var groupRule =
        new MappingRuleEntity("group", 1L, "$.region", "EMEA", "group", "GROUP_ATTRIBUTE");
    final var rules = new SearchQueryResult<>(1, false, List.of(groupRule), null, null);
    when(client.searchMappingRules(any())).thenReturn(rules);
    final Map<String, Object> region = Map.of("region", "EMEA");

    // when / then
    assertThat(
            services.getMatchingMappingRules(
                Map.of(), Map.of(), List.of(Map.of("region", "APAC"), region), authentication))
        .extracting(MappingRuleEntity::mappingRuleId)
        .containsExactly("group");
    assertThat(
            services.getMatchingMappingRules(
                Map.of(), Map.of(), List.of(Map.of("region", "APAC")), authentication))
        .isEmpty();
    // token claims and user attributes never satisfy a group-attribute rule
    assertThat(services.getMatchingMappingRules(region, region, List.of(), authentication))
        .isEmpty();
    assertThat(services.getMatchingMappingRules(region, region, authentication)).isEmpty();
  }

  @Test
  public void shouldNotMatchAttributeRulesAgainstTokenClaims() {
    // given
    final var attributeRule =
        new MappingRuleEntity(
            "attribute", 2L, "$.department", "Finance", "attribute", "USER_ATTRIBUTE");
    final var rules = new SearchQueryResult<>(1, false, List.of(attributeRule), null, null);
    when(client.searchMappingRules(any())).thenReturn(rules);
    final Map<String, Object> spoofedClaims = Map.of("department", "Finance");

    // when / then
    assertThat(services.getMatchingMappingRules(spoofedClaims, authentication)).isEmpty();
    assertThat(services.getMatchingMappingRules(spoofedClaims, Map.of(), authentication)).isEmpty();
    assertThat(
            services.getMatchingMappingRules(
                spoofedClaims, Map.of("department", "Sales"), authentication))
        .isEmpty();
  }

  @Test
  public void shouldNotMatchTokenRulesAgainstUserAttributes() {
    // given
    final var tokenRule = new MappingRuleEntity("token", 1L, "$.team", "red", "token");
    final var rules = new SearchQueryResult<>(1, false, List.of(tokenRule), null, null);
    when(client.searchMappingRules(any())).thenReturn(rules);

    // when / then
    assertThat(services.getMatchingMappingRules(Map.of(), Map.of("team", "red"), authentication))
        .isEmpty();
  }

  @Test
  public void shouldSendOriginOnCreateAndUpdate() {
    // given
    final var dto =
        new MappingRuleDTO(
            "$.department",
            "Finance",
            "name",
            "mappingRuleId",
            MappingRuleType.USER_ATTRIBUTE,
            "entra");

    // when
    services.createMappingRule(dto, authentication);
    services.updateMappingRule(dto, authentication);

    // then
    final var requests = stubbedBrokerClient.getBrokerRequests();
    assertThat(requests)
        .extracting(request -> ((MappingRuleRecord) request.getRequestWriter()).getOrigin())
        .containsExactly("entra", "entra");
  }

  @Test
  public void shouldOnlyMatchRulesWithoutOriginWhenNoSourcesAreGiven() {
    // given
    final var anyRule = new MappingRuleEntity("any", 1L, "$.team", "red", "any");
    final var entraRule =
        new MappingRuleEntity("entra", 2L, "$.team", "red", "entra", "TOKEN_CLAIM", "entra");
    final var rules = new SearchQueryResult<>(2, false, List.of(anyRule, entraRule), null, null);
    when(client.searchMappingRules(any())).thenReturn(rules);

    // when / then
    assertThat(services.getMatchingMappingRules(Map.of("team", "red"), authentication))
        .extracting(MappingRuleEntity::mappingRuleId)
        .containsExactly("any");
  }

  @Test
  public void shouldMatchTokenRulesWithOriginOnlyForLoginOfThatSource() {
    // given
    final var anyRule = new MappingRuleEntity("any", 1L, "$.team", "red", "any");
    final var entraRule =
        new MappingRuleEntity("entra", 2L, "$.team", "red", "entra", "TOKEN_CLAIM", "entra");
    final var rules = new SearchQueryResult<>(2, false, List.of(anyRule, entraRule), null, null);
    when(client.searchMappingRules(any())).thenReturn(rules);
    final Map<String, Object> claims = Map.of("team", "red");

    // when / then
    assertThat(services.getMatchingMappingRules(claims, null, null, "entra", null, authentication))
        .extracting(MappingRuleEntity::mappingRuleId)
        .containsExactlyInAnyOrder("any", "entra");
    assertThat(
            services.getMatchingMappingRules(claims, null, null, "keycloak", null, authentication))
        .extracting(MappingRuleEntity::mappingRuleId)
        .containsExactly("any");
    assertThat(services.getMatchingMappingRules(claims, null, null, null, "entra", authentication))
        .extracting(MappingRuleEntity::mappingRuleId)
        .containsExactly("any");
  }

  @Test
  public void shouldMatchAttributeRulesWithOriginOnlyForUserOfThatSource() {
    // given
    final var entraRule =
        new MappingRuleEntity(
            "entra", 1L, "$.department", "Finance", "entra", "USER_ATTRIBUTE", "entra");
    final var rules = new SearchQueryResult<>(1, false, List.of(entraRule), null, null);
    when(client.searchMappingRules(any())).thenReturn(rules);
    final Map<String, Object> attributes = Map.of("department", "Finance");

    // when / then
    assertThat(
            services.getMatchingMappingRules(
                Map.of(), attributes, null, null, "entra", authentication))
        .extracting(MappingRuleEntity::mappingRuleId)
        .containsExactly("entra");
    assertThat(
            services.getMatchingMappingRules(
                Map.of(), attributes, null, "entra", "keycloak", authentication))
        .isEmpty();
    assertThat(
            services.getMatchingMappingRules(
                Map.of(), attributes, null, "entra", "", authentication))
        .isEmpty();
  }

  @Test
  public void shouldOnlyConsiderGroupsOfTheUsersSourceForGroupAttributeRules() {
    // given
    final var anyRule =
        new MappingRuleEntity("any", 1L, "$.region", "EMEA", "any", "GROUP_ATTRIBUTE");
    final var rules = new SearchQueryResult<>(1, false, List.of(anyRule), null, null);
    when(client.searchMappingRules(any())).thenReturn(rules);
    final Map<String, Object> region = Map.of("region", "EMEA");

    // when / then
    assertThat(
            services.getMatchingMappingRules(
                Map.of(),
                Map.of(),
                List.of(new MappingRuleServices.SourcedAttributes("keycloak", region)),
                null,
                "entra",
                authentication))
        .isEmpty();
    assertThat(
            services.getMatchingMappingRules(
                Map.of(),
                Map.of(),
                List.of(new MappingRuleServices.SourcedAttributes("entra", region)),
                null,
                "entra",
                authentication))
        .extracting(MappingRuleEntity::mappingRuleId)
        .containsExactly("any");
    assertThat(
            services.getMatchingMappingRules(
                Map.of(),
                Map.of(),
                List.of(new MappingRuleServices.SourcedAttributes("keycloak", region)),
                null,
                null,
                authentication))
        .extracting(MappingRuleEntity::mappingRuleId)
        .containsExactly("any");
  }
}
