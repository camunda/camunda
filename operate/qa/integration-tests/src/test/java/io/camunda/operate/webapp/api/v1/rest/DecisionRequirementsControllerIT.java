/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.webapp.api.v1.rest.DecisionRequirementsController.AS_XML;
import static io.camunda.operate.webapp.api.v1.rest.DecisionRequirementsController.URI;
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
import io.camunda.operate.webapp.api.v1.dao.DecisionRequirementsDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
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
@SpringBootTest(classes = {TestApplicationWithNoBeans.class, DecisionRequirementsController.class})
public class DecisionRequirementsControllerIT {

  @Autowired WebApplicationContext context;

  private MockMvc mockMvc;

  @MockBean private DecisionRequirementsDao decisionRequirementsDao;
  @MockBean private PermissionsService permissionsService;

  @Before
  public void setupMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void shouldReturnDecisionRequirementsForByKeyAsJSON() throws Exception {
    final String expectedJSONContent =
        "{\"key\":0,\"decisionRequirementsId\":\"drdId-0\",\"name\":\"name-0\",\"version\":0}";
    // given
    final List<DecisionRequirements> decisionRequirements = createDecisionRequirementsOf(1);
    when(decisionRequirementsDao.byKey(0L)).thenReturn(decisionRequirements.get(0));
    // then
    assertGetToSucceed(URI + "/0").andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldReturnErrorMessageForByKeyFailure() throws Exception {
    final String expectedJSONContent =
        "{\"status\":404,\"message\":\"Error in retrieving data for key.\",\"instance\":\"ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c\",\"type\":\"Requested resource not found\"}";
    // given
    when(decisionRequirementsDao.byKey(any(Long.class)))
        .thenThrow(
            new ResourceNotFoundException("Error in retrieving data for key.")
                .setInstance("ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c"));
    // then
    assertGetWithFailed(URI + "/235")
        .andExpect(status().isNotFound())
        .andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldReturnDecisionRequirementsXmlByKey() throws Exception {
    final String expectedXMLContent = "<xml><value/></xml>";
    // given
    when(decisionRequirementsDao.xmlByKey(0L)).thenReturn(expectedXMLContent);
    // then
    assertGetToSucceed(URI + "/0" + AS_XML)
        .andExpect(content().contentType("text/xml;charset=UTF-8"))
        .andExpect(content().string(expectedXMLContent));
  }

  @Test
  public void shouldReturnErrorMessageForXmlByKeyFailure() throws Exception {
    final String expectedJSONContent =
        "{\"status\":500,\"message\":\"Error in retrieving data for key.\",\"instance\":\"instanceValue\",\"type\":\"API application error\"}";
    // given
    when(decisionRequirementsDao.xmlByKey(any(Long.class)))
        .thenThrow(
            new ServerException("Error in retrieving data for key.").setInstance("instanceValue"));
    // then
    assertGetWithFailed(URI + "/235" + AS_XML)
        .andExpect(status().isInternalServerError())
        .andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldAcceptEmptyJSONQuery() throws Exception {
    assertPostToWithSucceed(DecisionRequirementsController.URI + SEARCH, "{}");
    verify(decisionRequirementsDao).search(new Query<>());
  }

  @Test
  public void shouldAcceptEmptyQuery() throws Exception {
    assertPostToWithSucceed(DecisionRequirementsController.URI + SEARCH, "");
    verify(decisionRequirementsDao).search(new Query<>());
  }

  @Test
  public void shouldAcceptQueryWithSearchAfterAndSize() throws Exception {
    assertPostToWithSucceed(
        DecisionRequirementsController.URI + SEARCH, "{\"searchAfter\": [\"name\"], \"size\": 7}");
    verify(decisionRequirementsDao)
        .search(new Query<DecisionRequirements>().setSearchAfter(new Object[] {"name"}).setSize(7));
  }

  @Test
  public void shouldAcceptQueryWithSizeAndSortSpec() throws Exception {
    assertPostToWithSucceed(
        DecisionRequirementsController.URI + SEARCH,
        "{\"size\": 7, \"sort\":  [{\"field\":\"name\", \"order\":\"DESC\"}] }");
    verify(decisionRequirementsDao)
        .search(
            new Query<DecisionRequirements>()
                .setSize(7)
                .setSort(Query.Sort.listOf(DecisionRequirements.NAME, Query.Sort.Order.DESC)));
  }

  @Test
  public void shouldAcceptQueryWithFilter() throws Exception {
    assertPostToWithSucceed(
        DecisionRequirementsController.URI + SEARCH, "{\"filter\": { \"name\": \"hase\" } }");
    verify(decisionRequirementsDao)
        .search(
            new Query<DecisionRequirements>()
                .setFilter(new DecisionRequirements().setName("hase")));
  }

  @Test
  public void shouldAcceptQueryWithFullFilterAndSortingAndPaging() throws Exception {
    assertPostToWithSucceed(
        DecisionRequirementsController.URI + SEARCH,
        "{\"filter\": "
            + "{ \"name\": \"hase\","
            + "\"version\": 5 ,"
            + "\"decisionRequirementsId\": \"decisionRequirementsId-23\", "
            + "\"key\": 4217, "
            + "\"tenantId\": \"tenantA\""
            + "},"
            + "\"size\": 17, "
            + "\"sort\": [{\"field\":\"version\", \"order\":\"DESC\"}]"
            + "}");
    verify(decisionRequirementsDao)
        .search(
            new Query<DecisionRequirements>()
                .setFilter(
                    new DecisionRequirements()
                        .setName("hase")
                        .setVersion(5)
                        .setDecisionRequirementsId("decisionRequirementsId-23")
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
    when(decisionRequirementsDao.search(any(Query.class)))
        .thenThrow(
            new ServerException("Error in retrieving data.")
                .setInstance("47a7e1e4-5f09-4086-baa0-c9bcd40da029"));
    // then
    assertPostToWithFailed(DecisionRequirementsController.URI + SEARCH, "{}")
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
  public void shouldReturnForbiddenWhenUnauthorizedForByKeyXml() throws Exception {
    // given
    doThrow(new ForbiddenException("Unauthorized"))
        .when(permissionsService)
        .verifyWildcardResourcePermission(any(), any());

    // when - then
    assertGetWithFailed(URI + "/235" + AS_XML).andExpect(status().is(HttpStatus.FORBIDDEN.value()));
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

  protected List<DecisionRequirements> createDecisionRequirementsOf(final int number) {
    final List<DecisionRequirements> decisionRequirements = new ArrayList<>();
    for (int i = 0; i < number; i++) {
      decisionRequirements.add(
          new DecisionRequirements()
              .setDecisionRequirementsId("drdId-" + i)
              .setName("name-" + i)
              .setKey(i)
              .setVersion(i));
    }
    return decisionRequirements;
  }
}
