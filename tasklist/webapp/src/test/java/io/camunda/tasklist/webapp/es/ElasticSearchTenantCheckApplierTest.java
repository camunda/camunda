/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.webapp.es.tenant.ElasticsearchTenantCheckApplier;
import io.camunda.tasklist.webapp.tenant.TenantService;
import java.util.Collections;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticSearchTenantCheckApplierTest {

  @Mock private TenantService tenantService;

  @InjectMocks private ElasticsearchTenantCheckApplier instance;

  @Test
  void checkIfQueryContainsTenant() {
    // given
    final SearchRequest searchRequest = new SearchRequest("TaskTest");
    searchRequest.source().query(termsQuery("test", "1"));
    final TenantService.AuthenticatedTenants authenticatedTenants = mock();
    final List<String> authorizedTenant = List.of("TenantA", "TenantB");
    when(authenticatedTenants.getTenantIds()).thenReturn(authorizedTenant);
    final String queryResult =
        "{\n"
            + "  \"bool\" : {\n"
            + "    \"must\" : [\n"
            + "      {\n"
            + "        \"terms\" : {\n"
            + "          \"tenantId\" : [\n"
            + "            \"TenantA\",\n"
            + "            \"TenantB\"\n"
            + "          ],\n"
            + "          \"boost\" : 1.0\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        \"terms\" : {\n"
            + "          \"test\" : [\n"
            + "            \"1\"\n"
            + "          ],\n"
            + "          \"boost\" : 1.0\n"
            + "        }\n"
            + "      }\n"
            + "    ],\n"
            + "    \"adjust_pure_negative\" : true,\n"
            + "    \"boost\" : 1.0\n"
            + "  }\n"
            + "}";

    when(authenticatedTenants.getTenantAccessType())
        .thenReturn(TenantService.TenantAccessType.TENANT_ACCESS_ASSIGNED);
    when(tenantService.getAuthenticatedTenants()).thenReturn(authenticatedTenants);

    // when
    instance.apply(searchRequest);

    // then
    assertThat(searchRequest.source().query().toString()).isEqualTo(queryResult);
  }

  @Test
  void checkIfQueryContainsAccessibleTenantsProvidedByUser() {
    // given
    final SearchRequest searchRequest = new SearchRequest("TaskTest");
    searchRequest.source().query(termsQuery("test", "1"));
    final TenantService.AuthenticatedTenants authenticatedTenants = mock();
    final List<String> tenantsProvidedByUser = List.of("TenantA", "TenantC");
    final List<String> authorizedTenant = List.of("TenantA", "TenantB");
    when(authenticatedTenants.getTenantIds()).thenReturn(authorizedTenant);
    final String queryResult =
        "{\n"
            + "  \"bool\" : {\n"
            + "    \"must\" : [\n"
            + "      {\n"
            + "        \"terms\" : {\n"
            + "          \"tenantId\" : [\n"
            + "            \"TenantA\"\n"
            + "          ],\n"
            + "          \"boost\" : 1.0\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        \"terms\" : {\n"
            + "          \"test\" : [\n"
            + "            \"1\"\n"
            + "          ],\n"
            + "          \"boost\" : 1.0\n"
            + "        }\n"
            + "      }\n"
            + "    ],\n"
            + "    \"adjust_pure_negative\" : true,\n"
            + "    \"boost\" : 1.0\n"
            + "  }\n"
            + "}";

    when(authenticatedTenants.getTenantAccessType())
        .thenReturn(TenantService.TenantAccessType.TENANT_ACCESS_ASSIGNED);
    when(tenantService.getAuthenticatedTenants()).thenReturn(authenticatedTenants);

    // when
    instance.apply(searchRequest, tenantsProvidedByUser);

    // then
    assertThat(searchRequest.source().query().toString()).isEqualTo(queryResult);
  }

  @Test
  void checkShouldReturnNoneMatchQueryIfUserProvidedNotAccessibleTenants() {
    // given
    final SearchRequest searchRequest = new SearchRequest("TaskTest");
    searchRequest.source().query(termsQuery("test", "1"));
    final TenantService.AuthenticatedTenants authenticatedTenants = mock();
    final List<String> tenantsProvidedByUser = List.of("UnknownTenant");
    final List<String> authorizedTenant = List.of("TenantA", "TenantB");
    when(authenticatedTenants.getTenantIds()).thenReturn(authorizedTenant);

    when(authenticatedTenants.getTenantAccessType())
        .thenReturn(TenantService.TenantAccessType.TENANT_ACCESS_ASSIGNED);
    when(tenantService.getAuthenticatedTenants()).thenReturn(authenticatedTenants);

    // when
    instance.apply(searchRequest, tenantsProvidedByUser);

    // then
    assertThat(searchRequest.source().query().toString())
        .isEqualTo(ElasticsearchUtil.createMatchNoneQuery().toString());
  }

  @Test
  void checkShouldReturnNoneMatchQueryIfUserHaveNoneTenantsAccess() {
    // given
    final SearchRequest searchRequest = new SearchRequest("TaskTest");
    searchRequest.source().query(termsQuery("test", "1"));
    final TenantService.AuthenticatedTenants authenticatedTenants = mock();
    when(tenantService.getAuthenticatedTenants()).thenReturn(authenticatedTenants);
    when(authenticatedTenants.getTenantIds()).thenReturn(Collections.emptyList());
    when(authenticatedTenants.getTenantAccessType())
        .thenReturn(TenantService.TenantAccessType.TENANT_ACCESS_NONE);

    // when
    instance.apply(searchRequest);

    // then
    assertThat(searchRequest.source().query().toString())
        .isEqualTo(ElasticsearchUtil.createMatchNoneQuery().toString());
  }

  @Test
  void checkThatQueryDontContainTenantWhenMultiTenancyIsTurnedOff() {
    // given
    final SearchRequest searchRequest = new SearchRequest("TaskTest");
    searchRequest.source().query(termsQuery("test", "1"));
    final TenantService.AuthenticatedTenants authenticatedTenants = mock();
    when(authenticatedTenants.getTenantIds()).thenReturn(Collections.emptyList());
    when(authenticatedTenants.getTenantAccessType())
        .thenReturn(TenantService.TenantAccessType.TENANT_ACCESS_ALL);
    when(tenantService.getAuthenticatedTenants()).thenReturn(authenticatedTenants);
    final String expectedQueryResult =
        "{\n"
            + "  \"terms\" : {\n"
            + "    \"test\" : [\n"
            + "      \"1\"\n"
            + "    ],\n"
            + "    \"boost\" : 1.0\n"
            + "  }\n"
            + "}";

    // when
    instance.apply(searchRequest);

    // then
    assertThat(searchRequest.source().query().toString()).isEqualTo(expectedQueryResult);
  }
}
