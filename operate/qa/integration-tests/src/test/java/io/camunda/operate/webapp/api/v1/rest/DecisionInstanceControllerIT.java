/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.webapp.api.v1.rest.DecisionInstanceController.URI;
import static io.camunda.operate.webapp.api.v1.rest.SearchController.SEARCH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.api.v1.dao.DecisionInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.ForbiddenException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
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
@SpringBootTest(classes = {TestApplicationWithNoBeans.class, DecisionInstanceController.class})
public class DecisionInstanceControllerIT {

  @Autowired WebApplicationContext context;

  private MockMvc mockMvc;

  @MockBean private DecisionInstanceDao decisionInstanceDao;
  @MockBean private PermissionsService permissionsService;

  @Before
  public void setupMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void shouldReturnDecisionInstanceForByIdAsJSON() throws Exception {
    final String expectedJSONContent =
        "{\"id\":\"0-1\",\"key\":0,\"processDefinitionKey\":0,\"decisionId\":\"decisionId-0\",\"decisionName\":\"decisionName-0\",\"decisionVersion\":0}";
    // given
    final List<DecisionInstance> decisionInstances = createDecisionInstancesOf(1);
    when(decisionInstanceDao.byId("0")).thenReturn(decisionInstances.get(0));
    // then
    assertGetToSucceed(URI + "/0").andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldReturnErrorMessageForByIdFailure() throws Exception {
    final String expectedJSONContent =
        "{\"status\":404,\"message\":\"Error in retrieving data for id.\",\"instance\":\"ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c\",\"type\":\"Requested resource not found\"}";
    // given
    when(decisionInstanceDao.byId(any(String.class)))
        .thenThrow(
            new ResourceNotFoundException("Error in retrieving data for id.")
                .setInstance("ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c"));
    // then
    assertGetWithFailed(URI + "/235")
        .andExpect(status().isNotFound())
        .andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldAcceptEmptyJSONQuery() throws Exception {
    assertPostToWithSucceed(DecisionInstanceController.URI + SEARCH, "{}");
    verify(decisionInstanceDao).search(new Query<>());
  }

  @Test
  public void shouldAcceptEmptyQuery() throws Exception {
    assertPostToWithSucceed(DecisionInstanceController.URI + SEARCH, "");
    verify(decisionInstanceDao).search(new Query<>());
  }

  @Test
  public void shouldAcceptQueryWithSearchAfterAndSize() throws Exception {
    assertPostToWithSucceed(
        DecisionInstanceController.URI + SEARCH,
        "{\"searchAfter\": [\"decisionName\"], \"size\": 7}");
    verify(decisionInstanceDao)
        .search(
            new Query<DecisionInstance>().setSearchAfter(new Object[] {"decisionName"}).setSize(7));
  }

  @Test
  public void shouldAcceptQueryWithSizeAndSortSpec() throws Exception {
    assertPostToWithSucceed(
        DecisionInstanceController.URI + SEARCH,
        "{\"size\": 7, \"sort\":  [{\"field\":\"decisionName\", \"order\":\"DESC\"}] }");
    verify(decisionInstanceDao)
        .search(
            new Query<DecisionInstance>()
                .setSize(7)
                .setSort(Query.Sort.listOf(DecisionInstance.DECISION_NAME, Query.Sort.Order.DESC)));
  }

  @Test
  public void shouldAcceptQueryWithFilter() throws Exception {
    assertPostToWithSucceed(
        DecisionInstanceController.URI + SEARCH, "{\"filter\": { \"decisionName\": \"hase\" } }");
    verify(decisionInstanceDao)
        .search(
            new Query<DecisionInstance>()
                .setFilter(new DecisionInstance().setDecisionName("hase")));
  }

  @Test
  public void shouldAcceptQueryWithFullFilterAndSortingAndPaging() throws Exception {
    assertPostToWithSucceed(
        DecisionInstanceController.URI + SEARCH,
        "{\"filter\": "
            + "{ \"decisionName\": \"hase\","
            + "\"decisionVersion\": 5 ,"
            + "\"decisionId\": \"decisionId-23\", "
            + "\"key\": 4217, "
            + "\"tenantId\": \"tenantA\""
            + "},"
            + "\"size\": 17, "
            + "\"sort\": [{\"field\":\"decisionVersion\", \"order\":\"DESC\"}]"
            + "}");
    verify(decisionInstanceDao)
        .search(
            new Query<DecisionInstance>()
                .setFilter(
                    new DecisionInstance()
                        .setDecisionName("hase")
                        .setDecisionVersion(5)
                        .setDecisionId("decisionId-23")
                        .setKey(4217L)
                        .setTenantId("tenantA"))
                .setSort(
                    Query.Sort.listOf(
                        DecisionInstanceTemplate.DECISION_VERSION, Query.Sort.Order.DESC))
                .setSize(17));
  }

  @Test
  public void shouldReturnErrorMessageForListFailure() throws Exception {
    final String expectedJSONContent =
        "{\"status\":500,\"message\":\"Error in retrieving data.\",\"instance\":\"47a7e1e4-5f09-4086-baa0-c9bcd40da029\",\"type\":\"API application error\"}";
    // given
    when(decisionInstanceDao.search(any(Query.class)))
        .thenThrow(
            new ServerException("Error in retrieving data.")
                .setInstance("47a7e1e4-5f09-4086-baa0-c9bcd40da029"));
    // then
    assertPostToWithFailed(DecisionInstanceController.URI + SEARCH, "{}")
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

  protected List<DecisionInstance> createDecisionInstancesOf(final int number) {
    final List<DecisionInstance> decisionInstances = new ArrayList<>();
    for (int i = 0; i < number; i++) {
      decisionInstances.add(
          new DecisionInstance()
              .setId(i + "-1")
              .setKey(i)
              .setProcessDefinitionKey(i)
              .setDecisionId("decisionId-" + i)
              .setDecisionName("decisionName-" + i)
              .setDecisionVersion(i));
    }
    return decisionInstances;
  }
}
