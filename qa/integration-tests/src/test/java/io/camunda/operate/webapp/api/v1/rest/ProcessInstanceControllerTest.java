/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.webapp.api.v1.entities.ProcessInstance.VERSION;
import static io.camunda.operate.webapp.api.v1.rest.ProcessInstanceController.URI;
import static io.camunda.operate.webapp.api.v1.rest.SearchController.SEARCH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.api.v1.dao.FlowNodeStatisticsDao;
import io.camunda.operate.webapp.api.v1.dao.ProcessInstanceDao;
import io.camunda.operate.webapp.api.v1.dao.SequenceFlowDao;
import io.camunda.operate.webapp.api.v1.entities.ChangeStatus;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeStatistics;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort.Order;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.entities.SequenceFlow;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
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

import java.util.Arrays;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        TestApplicationWithNoBeans.class,
        ProcessInstanceController.class
    })
public class ProcessInstanceControllerTest {

  @Autowired
  WebApplicationContext context;

  private MockMvc mockMvc;

  @MockBean
  private ProcessInstanceDao processInstanceDao;

  @MockBean
  private SequenceFlowDao sequenceFlowDao;

  @MockBean
  private FlowNodeStatisticsDao flowNodeStatisticsDao;

  @Before
  public void setupMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void shouldAcceptEmptyQuery() throws Exception {
    assertPostToWithSucceed(URI + SEARCH, "{}");
    verify(processInstanceDao).search(new Query<>());
  }

  @Test
  public void shouldAcceptQueryWithSearchAfterAndSize() throws Exception {
    assertPostToWithSucceed(URI + SEARCH, "{\"searchAfter\": [\"" + VERSION+"\"], \"size\": 7}");
    verify(processInstanceDao).search(new Query<ProcessInstance>().setSearchAfter(new Object[]{
        VERSION}).setSize(7));
  }

  @Test
  public void shouldAcceptQueryWithSizeAndSortSpec() throws Exception {
    assertPostToWithSucceed(URI + SEARCH,
        "{\"size\": 7, \"sort\": [ "+ getSortFor(VERSION, Order.DESC)+" ] }");
    verify(processInstanceDao).search(new Query<ProcessInstance>()
        .setSize(7)
        .setSort(Sort.listOf(VERSION,Order.DESC)));
  }

  private String getSortFor(String field, Query.Sort.Order order) {
    return "{ \"field\":\"" + field + "\", \"order\": \"" + order.name() + "\" }";
  }

  @Test
  public void shouldAcceptQueryWithFilter() throws Exception {
    assertPostToWithSucceed(URI + SEARCH, "{\"filter\": { \""+ VERSION +"\": \"1\" } }");
    verify(processInstanceDao).search(new Query<ProcessInstance>()
        .setFilter(new ProcessInstance().setProcessVersion(1)));
  }

  @Test
  public void shouldAcceptQueryWithFullFilterAndSortingAndPaging() throws Exception {
    assertPostToWithSucceed(URI + SEARCH,
        "{\"filter\": "
            + "{ "
            + "\"processVersion\": 5 ,"
            + "\"bpmnProcessId\": \"bpmnProcessId-23\", "
            + "\"key\": 4217"
            + "},"
            + "\"size\": 17, "
            + "\"sort\": ["+ getSortFor(VERSION, Order.DESC)+ "]"
            + "}");
    verify(processInstanceDao).search(new Query<ProcessInstance>()
        .setFilter(
            new ProcessInstance()
                .setBpmnProcessId("bpmnProcessId-23")
                .setProcessVersion(5)
                .setKey(4217L))
        .setSort(Sort.listOf(VERSION, Order.DESC))
        .setSize(17));
  }

  @Test
  public void shouldReturnErrorMessageForListFailure() throws Exception {
    final String expectedJSONContent = "{\"status\":500,\"message\":\"Error in retrieving data.\",\"instance\":\"47a7e1e4-5f09-4086-baa0-c9bcd40da029\",\"type\":\"API application error\"}";
    // given
    when(processInstanceDao.search(any(Query.class))).thenThrow(
        new ServerException("Error in retrieving data.").setInstance("47a7e1e4-5f09-4086-baa0-c9bcd40da029"));
    // then
    assertPostToWithFailed(URI + SEARCH, "{}")
        .andExpect(status().isInternalServerError())
        .andExpect(content().string(expectedJSONContent));
  }


  @Test
  public void shouldReturnErrorMessageForByKeyFailure() throws Exception {
    final String expectedJSONContent = "{\"status\":404,\"message\":\"Error in retrieving data for key.\",\"instance\":\"ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c\",\"type\":\"Requested resource not found\"}";
    // given
    when(processInstanceDao.byKey(any(Long.class))).thenThrow(
        new ResourceNotFoundException("Error in retrieving data for key.")
            .setInstance("ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c"));
    // then
    assertGetWithFailed(URI + "/235")
        .andExpect(status().isNotFound())
        .andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldDeleteByKey() throws Exception {
    final String expectedJSONContent = "{\"message\":\"Is deleted\",\"deleted\":1}";
    // given
    when(processInstanceDao.delete(123L)).thenReturn(
        new ChangeStatus().setDeleted(1).setMessage("Is deleted"));
    // then
    mockMvc.perform(delete(URI+"/123"))
        .andExpect(status().isOk())
        .andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldReturnErrorMessageForDeleteByKeyFailure() throws Exception {
    final String expectedJSONContent = "{\"status\":404,\"message\":\"Not found\",\"instance\":\"instanceValue\",\"type\":\"Requested resource not found\"}";
    // given
    when(processInstanceDao.delete(123L)).thenThrow(
        new ResourceNotFoundException("Not found").setInstance("instanceValue"));
    // then
    mockMvc.perform(delete(URI + "/123"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldReturnEmptyListWhenNoSequenceFlows() throws Exception {
    final String expectedJSONContent = "[]";
    final Long processInstanceKey = 123L;
    // given
    Results<SequenceFlow> results = new Results<>();
    when(sequenceFlowDao.search(new Query<SequenceFlow>().setFilter(new SequenceFlow().setProcessInstanceKey(processInstanceKey)))).thenReturn(results);
    // then
    assertGetWithSucceed(String.format("%s/%s/sequence-flows", URI, processInstanceKey)).andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldReturnSequenceFlows() throws Exception {
    final String expectedJSONContent = "[\"SF1\",\"SF2\"]";
    final Long processInstanceKey = 123L;
    // given
    Results<SequenceFlow> results = new Results<>();
    results.getItems().addAll(Arrays.asList(new SequenceFlow().setActivityId("SF1"), new SequenceFlow().setActivityId("SF2")));
    when(sequenceFlowDao.search(new Query<SequenceFlow>().setFilter(new SequenceFlow().setProcessInstanceKey(processInstanceKey)))).thenReturn(results);
    // then
    assertGetWithSucceed(String.format("%s/%s/sequence-flows", URI, processInstanceKey)).andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldReturnFlowNodeStatistics() throws Exception {
    final String expectedJSONContent = "[{\"activityId\":\"A1\",\"active\":1,\"canceled\":2,\"incidents\":4,\"completed\":3},{\"activityId\":\"A2\",\"active\":5,\"canceled\":6,\"incidents\":8,\"completed\":7}]";
    final Long processInstanceKey = 123L;
    // given
    List<FlowNodeStatistics> results = Arrays.asList(
        new FlowNodeStatistics().setActivityId("A1").setActive(1L).setCanceled(2L).setCompleted(3L).setIncidents(4L),
        new FlowNodeStatistics().setActivityId("A2").setActive(5L).setCanceled(6L).setCompleted(7L).setIncidents(8L));
    when(flowNodeStatisticsDao.getFlowNodeStatisticsForProcessInstance(processInstanceKey)).thenReturn(results);
    // then
    assertGetWithSucceed(String.format("%s/%s/statistics", URI, processInstanceKey)).andExpect(content().string(expectedJSONContent));
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

  protected ResultActions assertGetWithSucceed(final String endpoint)
      throws Exception {
    return mockMvc.perform(get(endpoint))
        .andExpect(status().isOk());
  }

  protected ResultActions assertPostToWithSucceed(final String endpoint, final String content)
      throws Exception {
    return mockMvc.perform(post(endpoint)
            .content(content)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }
}
