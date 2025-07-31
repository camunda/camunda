/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.webapp.api.v1.rest.DecisionDefinitionController.URI;
import static io.camunda.operate.webapp.api.v1.rest.SearchController.SEARCH;
import static io.camunda.webapps.schema.descriptors.index.DecisionIndex.VERSION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.api.v1.dao.DecisionDefinitionDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.ForbiddenException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {TestApplicationWithNoBeans.class, DecisionDefinitionController.class})
public class DecisionDefinitionControllerIT {

  @Autowired WebApplicationContext context;

  private MockMvc mockMvc;

  @MockBean private DecisionDefinitionDao decisionDefinitionDao;
  @MockBean private PermissionsService permissionsService;

  @Before
  public void setupMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void shouldReturnDecisionDefinitionForByKeyAsJSON() throws Exception {
    final String expectedJSONContent =
        "{\"key\":0,\"decisionId\":\"decisionId-0\",\"name\":\"name-0\",\"version\":0}";
    // given
    final List<DecisionDefinition> decisionDefinitions = createDecisionDefinitionsOf(1);
    when(decisionDefinitionDao.byKey(0L)).thenReturn(decisionDefinitions.get(0));
    // then
    assertGetToSucceed(URI + "/0").andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldReturnErrorMessageForByKeyFailure() throws Exception {
    final String expectedJSONContent =
        "{\"status\":404,\"message\":\"Error in retrieving data for key.\",\"instance\":\"ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c\",\"type\":\"Requested resource not found\"}";
    // given
    when(decisionDefinitionDao.byKey(any(Long.class)))
        .thenThrow(
            new ResourceNotFoundException("Error in retrieving data for key.")
                .setInstance("ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c"));
    // then
    assertGetWithFailed(URI + "/235")
        .andExpect(status().isNotFound())
        .andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldAcceptEmptyJSONQuery() throws Exception {
    assertPostToWithSucceed(DecisionDefinitionController.URI + SEARCH, "{}");
    verify(decisionDefinitionDao).search(new Query<>());
  }

  @Test
  public void shouldAcceptEmptyQuery() throws Exception {
    assertPostToWithSucceed(DecisionDefinitionController.URI + SEARCH, "");
    verify(decisionDefinitionDao).search(new Query<>());
  }

  @Test
  public void shouldAcceptQueryWithSearchAfterAndSize() throws Exception {
    assertPostToWithSucceed(
        DecisionDefinitionController.URI + SEARCH, "{\"searchAfter\": [\"name\"], \"size\": 7}");
    verify(decisionDefinitionDao)
        .search(new Query<DecisionDefinition>().setSearchAfter(new Object[] {"name"}).setSize(7));
  }

  @Test
  public void shouldAcceptQueryWithSizeAndSortSpec() throws Exception {
    assertPostToWithSucceed(
        DecisionDefinitionController.URI + SEARCH,
        "{\"size\": 7, \"sort\":  [{\"field\":\"name\", \"order\":\"DESC\"}] }");
    verify(decisionDefinitionDao)
        .search(
            new Query<DecisionDefinition>()
                .setSize(7)
                .setSort(Query.Sort.listOf(DecisionDefinition.NAME, Query.Sort.Order.DESC)));
  }

  @Test
  public void shouldAcceptQueryWithFilter() throws Exception {
    assertPostToWithSucceed(
        DecisionDefinitionController.URI + SEARCH, "{\"filter\": { \"name\": \"hase\" } }");
    verify(decisionDefinitionDao)
        .search(
            new Query<DecisionDefinition>().setFilter(new DecisionDefinition().setName("hase")));
  }

  @Test
  public void shouldAcceptQueryWithFullFilterAndSortingAndPaging() throws Exception {
    assertPostToWithSucceed(
        DecisionDefinitionController.URI + SEARCH,
        "{\"filter\": "
            + "{ \"name\": \"hase\","
            + "\"version\": 5 ,"
            + "\"decisionId\": \"decisionId-23\", "
            + "\"key\": 4217, "
            + "\"tenantId\": \"tenantA\""
            + "},"
            + "\"size\": 17, "
            + "\"sort\": [{\"field\":\"version\", \"order\":\"DESC\"}]"
            + "}");
    verify(decisionDefinitionDao)
        .search(
            new Query<DecisionDefinition>()
                .setFilter(
                    new DecisionDefinition()
                        .setName("hase")
                        .setVersion(5)
                        .setDecisionId("decisionId-23")
                        .setKey(4217L)
                        .setTenantId("tenantA"))
                .setSort(Query.Sort.listOf(VERSION, Query.Sort.Order.DESC))
                .setSize(17));
  }

  @Test
  public void shouldReturnErrorMessageForListFailure() throws Exception {
    final String expectedJSONContent =
        "{\"status\":500,\"message\":\"Error in retrieving data.\",\"instance\":\"47a7e1e4-5f09-4086-baa0-c9bcd40da029\",\"type\":\"API application error\"}";
    // given
    when(decisionDefinitionDao.search(any(Query.class)))
        .thenThrow(
            new ServerException("Error in retrieving data.")
                .setInstance("47a7e1e4-5f09-4086-baa0-c9bcd40da029"));
    // then
    assertPostToWithFailed(DecisionDefinitionController.URI + SEARCH, "{}")
        .andExpect(status().isInternalServerError())
        .andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldReturnForbiddenWhenUnauthorizedForByKey() throws Exception {
    // given
    doThrow(new ForbiddenException("Unauthorized"))
        .when(permissionsService)
        .verifyWildcardResourcePermission(any(), any());

    // when - then
    assertGetWithFailed(URI + "/235").andExpect(status().is(HttpStatus.FORBIDDEN.value()));
  }

  @Test
  public void shouldReturnForbiddenWhenUnauthorizedForSearch() throws Exception {
    // given
    doThrow(new ForbiddenException("Unauthorized"))
        .when(permissionsService)
        .verifyWildcardResourcePermission(any(), any());

    // when - then
    assertPostToWithFailed(URI + SEARCH, "{}").andExpect(status().is(HttpStatus.FORBIDDEN.value()));
  }

  protected ResultActions assertGetToSucceed(final String endpoint) throws Exception {
    return mockMvc.perform(get(endpoint)).andExpect(status().isOk());
  }

  protected ResultActions assertGetWithFailed(final String endpoint) throws Exception {
    return mockMvc
        .perform(get(endpoint))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE));
  }

  protected ResultActions assertPostToWithFailed(final String endpoint, final String content)
      throws Exception {
    return mockMvc.perform(post(endpoint).content(content).contentType(MediaType.APPLICATION_JSON));
  }

  protected ResultActions assertPostToWithSucceed(final String endpoint, final String content)
      throws Exception {
    return mockMvc
        .perform(post(endpoint).content(content).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  protected List<DecisionDefinition> createDecisionDefinitionsOf(final int number) {
    final List<DecisionDefinition> decisionDefinitions = new ArrayList<>();
    for (int i = 0; i < number; i++) {
      decisionDefinitions.add(
          new DecisionDefinition()
              .setDecisionId("decisionId-" + i)
              .setName("name-" + i)
              .setKey(i)
              .setVersion(i));
    }
    return decisionDefinitions;
  }
}
