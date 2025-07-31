/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.webapp.api.v1.rest.FlowNodeInstanceController.URI;
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
import io.camunda.operate.webapp.api.v1.dao.FlowNodeInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort.Order;
import io.camunda.operate.webapp.api.v1.exceptions.ForbiddenException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
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
@SpringBootTest(classes = {TestApplicationWithNoBeans.class, FlowNodeInstanceController.class})
public class FlowNodeInstanceControllerIT {

  @Autowired WebApplicationContext context;
  @MockBean PermissionsService permissionsService;
  private MockMvc mockMvc;
  @MockBean private FlowNodeInstanceDao flowNodeInstanceDao;

  @Before
  public void setupMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void shouldAcceptEmptyJSONQuery() throws Exception {
    assertPostToWithSucceed(URI + SEARCH, "{}");
    verify(flowNodeInstanceDao).search(new Query<>());
  }

  @Test
  public void shouldAcceptEmptyQuery() throws Exception {
    assertPostToWithSucceed(URI + SEARCH, "");
    verify(flowNodeInstanceDao).search(new Query<>());
  }

  @Test
  public void shouldAcceptQueryWithSearchAfterAndSize() throws Exception {
    assertPostToWithSucceed(
        URI + SEARCH,
        "{\"searchAfter\": [\"" + FlowNodeInstance.PROCESS_INSTANCE_KEY + "\"], \"size\": 7}");
    verify(flowNodeInstanceDao)
        .search(
            new Query<FlowNodeInstance>()
                .setSearchAfter(new Object[] {FlowNodeInstance.PROCESS_INSTANCE_KEY})
                .setSize(7));
  }

  @Test
  public void shouldAcceptQueryWithSizeAndSortSpec() throws Exception {
    assertPostToWithSucceed(
        URI + SEARCH,
        "{\"size\": 7, \"sort\": [ "
            + getSortFor(FlowNodeInstance.START_DATE, Order.DESC)
            + " ] }");
    verify(flowNodeInstanceDao)
        .search(
            new Query<FlowNodeInstance>()
                .setSize(7)
                .setSort(Sort.listOf(FlowNodeInstance.START_DATE, Order.DESC)));
  }

  private String getSortFor(final String field, final Query.Sort.Order order) {
    return "{ \"field\":\"" + field + "\", \"order\": \"" + order.name() + "\" }";
  }

  @Test
  public void shouldAcceptQueryWithFilter() throws Exception {
    assertPostToWithSucceed(
        URI + SEARCH,
        "{\"filter\": { \"" + FlowNodeInstance.PROCESS_INSTANCE_KEY + "\": \"1\" } }");
    verify(flowNodeInstanceDao)
        .search(
            new Query<FlowNodeInstance>()
                .setFilter(new FlowNodeInstance().setProcessInstanceKey(1L)));
  }

  @Test
  public void shouldAcceptQueryWithFilterByFlowNodeId() throws Exception {
    final String flowNodeId = "StartEvent_1";
    assertPostToWithSucceed(
        URI + SEARCH,
        "{\"filter\": { \"" + FlowNodeInstance.FLOW_NODE_ID + "\": \"" + flowNodeId + "\" } }");
    verify(flowNodeInstanceDao)
        .search(
            new Query<FlowNodeInstance>()
                .setFilter(new FlowNodeInstance().setFlowNodeId(flowNodeId)));
  }

  @Test
  public void shouldAcceptQueryWithFilterByProcessDefinitionKey() throws Exception {
    final Long processDefinitionKey = 2251799813685251L;
    assertPostToWithSucceed(
        URI + SEARCH,
        "{\"filter\": { \""
            + FlowNodeInstance.PROCESS_DEFINITION_KEY
            + "\": \""
            + processDefinitionKey
            + "\" } }");
    verify(flowNodeInstanceDao)
        .search(
            new Query<FlowNodeInstance>()
                .setFilter(new FlowNodeInstance().setProcessDefinitionKey(processDefinitionKey)));
  }

  @Test
  public void shouldAcceptQueryWithFullFilterAndSortingAndPaging() throws Exception {
    assertPostToWithSucceed(
        URI + SEARCH,
        "{\"filter\": "
            + "{ "
            + "\"processInstanceKey\": 235 ,"
            + "\"type\": \"START_EVENT\", "
            + "\"key\": 4217, "
            + "\"tenantId\": \"tenantA\""
            + "},"
            + "\"size\": 17, "
            + "\"sort\": ["
            + getSortFor(FlowNodeInstance.START_DATE, Order.DESC)
            + "]"
            + "}");
    verify(flowNodeInstanceDao)
        .search(
            new Query<FlowNodeInstance>()
                .setFilter(
                    new FlowNodeInstance()
                        .setKey(4217L)
                        .setType("START_EVENT")
                        .setProcessInstanceKey(235L)
                        .setTenantId("tenantA"))
                .setSort(Sort.listOf(FlowNodeInstance.START_DATE, Order.DESC))
                .setSize(17));
  }

  @Test
  public void shouldReturnErrorMessageForListFailure() throws Exception {
    final String expectedJSONContent =
        "{\"status\":500,\"message\":\"Error in retrieving data.\",\"instance\":\"47a7e1e4-5f09-4086-baa0-c9bcd40da029\",\"type\":\"API application error\"}";
    // given
    when(flowNodeInstanceDao.search(any(Query.class)))
        .thenThrow(
            new ServerException("Error in retrieving data.")
                .setInstance("47a7e1e4-5f09-4086-baa0-c9bcd40da029"));
    // then
    assertPostToWithFailed(URI + SEARCH, "{}")
        .andExpect(status().isInternalServerError())
        .andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldReturnErrorMessageForByKeyFailure() throws Exception {
    final String expectedJSONContent =
        "{\"status\":404,\"message\":\"Error in retrieving data for key.\",\"instance\":\"ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c\",\"type\":\"Requested resource not found\"}";
    // given
    when(flowNodeInstanceDao.byKey(any(Long.class)))
        .thenThrow(
            new ResourceNotFoundException("Error in retrieving data for key.")
                .setInstance("ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c"));
    // then
    assertGetWithFailed(URI + "/235")
        .andExpect(status().isNotFound())
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
}
