/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.schema.indices.ProcessIndex.VERSION;
import static io.camunda.operate.webapp.api.v1.rest.ProcessDefinitionController.BY_KEY;
import static io.camunda.operate.webapp.api.v1.rest.ProcessDefinitionController.LIST;
import static io.camunda.operate.webapp.api.v1.rest.ProcessDefinitionController.URI;
import static io.camunda.operate.webapp.api.v1.rest.ProcessDefinitionController.XML_BY_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.api.v1.dao.ProcessDefinitionDao;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.api.v1.entities.Query.SortOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        TestApplicationWithNoBeans.class,
        ProcessDefinitionController.class
    })
public class ProcessDefinitionControllerTest {

  @Autowired
  WebApplicationContext context;

  private MockMvc mockMvc;

  @MockBean
  private ProcessDefinitionDao processDefinitionDao;

  @Before
  public void setupMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void shouldAcceptEmptyQuery() throws Exception {
    assertPostToWithSucceed(URI + LIST, "{}");
    verify(processDefinitionDao).listBy(new Query<>());
  }

  @Test
  public void shouldAcceptQueryWithFromAndSize() throws Exception {
    assertPostToWithSucceed(URI + LIST, "{\"from\":2, \"size\": 5}");
    verify(processDefinitionDao).listBy(new Query<ProcessDefinition>().setFrom(2).setSize(5));
  }

  @Test
  public void shouldAcceptQueryWithSearchAfterAndSize() throws Exception {
    assertPostToWithSucceed(URI + LIST, "{\"searchAfter\": [\"name\"], \"size\": 7}");
    verify(processDefinitionDao).listBy(new Query<ProcessDefinition>().setSearchAfter(new Object[]{"name"}).setSize(7));
  }

  @Test
  public void shouldAcceptQueryWithFromAndSizeAndSortSpec() throws Exception {
    assertPostToWithSucceed(URI + LIST, "{\"from\": 100, \"size\": 7, \"sortBy\": \"name\", \"sortOrder\": \"DESC\" }");
    verify(processDefinitionDao).listBy(new Query<ProcessDefinition>()
        .setFrom(100).setSize(7)
        .setSortBy("name").setSortOrder(SortOrder.DESC));
  }

  @Test
  public void shouldAcceptQueryWithExampleFilter() throws Exception {
    assertPostToWithSucceed(URI + LIST, "{\"example\": { \"name\": \"hase\" } }");
    verify(processDefinitionDao).listBy(new Query<ProcessDefinition>()
        .setExample(new ProcessDefinition().setName("hase")));
  }

  @Test
  public void shouldAcceptQueryWithFullExampleFilterAndSortingAndPaging() throws Exception {
    assertPostToWithSucceed(URI + LIST,
        "{\"example\": "
                + "{ \"name\": \"hase\","
                  + "\"version\": 5 ,"
                  + "\"bpmnProcessId\": \"bpmnProcessId-23\", "
                  + "\"key\": 4217"
                + "},"
                + "\"from\": 3, \"size\": 17, "
                + "\"sortBy\": \"version\", \"sortOrder\": \"DESC\""
            + "}");
    verify(processDefinitionDao).listBy(new Query<ProcessDefinition>()
        .setExample(
            new ProcessDefinition()
                .setName("hase")
                .setVersion(5)
                .setBpmnProcessId("bpmnProcessId-23")
                .setKey(4217L))
        .setSortBy(VERSION).setSortOrder(SortOrder.DESC)
        .setFrom(3).setSize(17));
  }

  @Test
  public void shouldReturnErrorMessageForListFailure() throws Exception {
    final String expectedJSONContent = "{\"status\":500,\"message\":\"Error in retrieving data.\",\"instance\":\"47a7e1e4-5f09-4086-baa0-c9bcd40da029\",\"type\":\"API application error\"}";
    // given
    when(processDefinitionDao.listBy(any(Query.class))).thenThrow(
        new ServerException("Error in retrieving data.").setInstance("47a7e1e4-5f09-4086-baa0-c9bcd40da029"));
    // then
    assertPostToWithFailed(URI + LIST, "{}")
        .andExpect(status().isInternalServerError())
        .andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldReturnProcessDefinitionForByKeyAsJSON() throws Exception {
    final String expectedJSONContent =
        "{\"key\":0,\"name\":\"name-0\",\"version\":0,\"bpmnProcessId\":\"bpmnProcessId-0\"}";
    // given
    List<ProcessDefinition> processDefinitions = createProcessDefinitionsOf(1);
    when(processDefinitionDao.byKey(0L)).thenReturn(processDefinitions.get(0));
    // then
    assertGetToSucceed(URI + BY_KEY + "/0")
        .andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldReturnErrorMessageForByKeyFailure() throws Exception {
    final String expectedJSONContent = "{\"status\":404,\"message\":\"Error in retrieving data for key.\",\"instance\":\"ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c\",\"type\":\"Requested resource not found\"}";
    // given
    when(processDefinitionDao.byKey(any(Long.class))).thenThrow(
        new ResourceNotFoundException("Error in retrieving data for key.")
            .setInstance("ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c"));
    // then
    assertGetWithFailed(URI + BY_KEY + "/235")
        .andExpect(status().isNotFound())
        .andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldReturnProcessDefinitionForXmlByKeyAsXML() throws Exception {
    final String expectedXMLContent = "<xml><value/></xml>";
    // given
    when(processDefinitionDao.xmlByKey(0L)).thenReturn(expectedXMLContent);
    // then
    assertGetToSucceed(URI + XML_BY_KEY + "/0")
        .andExpect(content().contentType("text/xml;charset=UTF-8"))
        .andExpect(content().string(expectedXMLContent));
  }

  @Test
  public void shouldReturnErrorMessageForXmlByKeyFailure() throws Exception {
    final String expectedJSONContent = "{\"status\":500,\"message\":\"Error in retrieving data for key.\",\"instance\":\"instanceValue\",\"type\":\"API application error\"}";
    // given
    when(processDefinitionDao.xmlByKey(any(Long.class))).thenThrow(
        new ServerException("Error in retrieving data for key.").setInstance("instanceValue"));
    // then
    assertGetWithFailed(URI + XML_BY_KEY + "/235")
        .andExpect(status().isInternalServerError())
        .andExpect(content().string(expectedJSONContent));
  }

  protected ResultActions assertGetToSucceed(final String endpoint) throws Exception {
    return mockMvc.perform(get(endpoint))
        .andExpect(status().isOk());
  }

  protected ResultActions assertGetWithFailed(final String endpoint) throws Exception {
    return mockMvc.perform(get(endpoint))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE));
  }

  protected ResultActions assertPostToWithFailed(final String endpoint, final String content)
      throws Exception {
    return mockMvc.perform(post(endpoint)
            .content(content)
            .contentType(MediaType.APPLICATION_JSON));
  }

  protected ResultActions assertPostToWithSucceed(final String endpoint, final String content)
      throws Exception {
    return mockMvc.perform(post(endpoint)
            .content(content)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  protected List<ProcessDefinition> createProcessDefinitionsOf(final int number) {
    final List<ProcessDefinition> processDefinitions = new ArrayList<>();
    for (int i = 0; i < number; i++) {
      processDefinitions.add(
          new ProcessDefinition()
              .setBpmnProcessId("bpmnProcessId-" + i)
              .setName("name-" + i)
              .setKey(i)
              .setVersion(i));
    }
    return processDefinitions;
  }

}
