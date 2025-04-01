/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.operate.util.j5templates.MockMvcManager;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.ListenerDto;
import io.camunda.operate.webapp.rest.dto.ListenerRequestDto;
import io.camunda.operate.webapp.rest.dto.ListenerResponseDto;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.webapps.schema.descriptors.operate.template.JobTemplate;
import io.camunda.webapps.schema.entities.JobEntity;
import io.camunda.webapps.schema.entities.listener.ListenerEventType;
import io.camunda.webapps.schema.entities.listener.ListenerState;
import io.camunda.webapps.schema.entities.listener.ListenerType;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class ListenerReaderIT extends OperateSearchAbstractIT {

  @Autowired MockMvcManager mockMvcManager;
  @Autowired JobTemplate jobTemplate;
  @Autowired private UserService userService;
  private String jobIndexName;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    jobIndexName = jobTemplate.getFullQualifiedName();
    objectMapper.registerModule(new JavaTimeModule());
    createData();
    searchContainerManager.refreshIndices("*operate*");
  }

  @Test
  public void testListenerReaderFlowNodeId() throws Exception {
    Mockito.when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId(DEFAULT_USER));

    final ListenerRequestDto request =
        new ListenerRequestDto().setPageSize(20).setFlowNodeId("test_task");
    final ListenerResponseDto response = postListenerRequest("111", request);
    final List<ListenerDto> resultListeners = response.getListeners();

    assertEquals(5L, response.getTotalCount());
    assertEquals(5, resultListeners.size());
    // results should be ordered by finish date (latest first, but no date at the beginning)
    final ListenerDto actual4 = resultListeners.get(0);
    assertEquals("22", actual4.getListenerKey());
    assertEquals(ListenerType.TASK_LISTENER, actual4.getListenerType());
    assertEquals(ListenerEventType.COMPLETING, actual4.getEvent());
    assertEquals(ListenerState.FAILED, actual4.getState());
    assertNull(actual4.getTime());

    final ListenerDto actual3 = resultListeners.get(1);
    assertEquals("21", actual3.getListenerKey());
    assertEquals(ListenerType.TASK_LISTENER, actual3.getListenerType());
    assertEquals(ListenerEventType.UPDATING, actual3.getEvent());
    assertEquals(ListenerState.ACTIVE, actual3.getState());
    assertNull(actual3.getTime());

    final ListenerDto actual0 = resultListeners.get(2);
    assertEquals("12", actual0.getListenerKey());
    assertEquals(ListenerType.EXECUTION_LISTENER, actual0.getListenerType());
    assertEquals(ListenerEventType.END, actual0.getEvent());
    assertEquals(ListenerState.COMPLETED, actual0.getState());
    assertEquals("test_type", actual0.getJobType());
    assertNotNull(actual0.getTime());

    final ListenerDto actual1 = resultListeners.get(3);
    assertEquals("11", actual1.getListenerKey());
    assertEquals(ListenerType.EXECUTION_LISTENER, actual1.getListenerType());
    assertEquals(ListenerEventType.START, actual1.getEvent());
    assertEquals(ListenerState.COMPLETED, actual1.getState());
    assertNotNull(actual1.getTime());

    final ListenerDto actual2 = resultListeners.get(4);
    assertEquals("31", actual2.getListenerKey());
    assertEquals(ListenerType.TASK_LISTENER, actual2.getListenerType());
    assertEquals(ListenerEventType.ASSIGNING, actual2.getEvent());
    assertEquals(ListenerState.UNKNOWN, actual2.getState());
    assertNotNull(actual2.getTime());
  }

  @Test
  public void testListenerReaderFlowNodeInstanceId() throws Exception {
    Mockito.when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId(DEFAULT_USER));

    final ListenerRequestDto request =
        new ListenerRequestDto().setPageSize(20).setFlowNodeInstanceId(1L);
    final ListenerResponseDto response = postListenerRequest("111", request);
    final List<ListenerDto> resultListeners = response.getListeners();

    assertEquals(3L, response.getTotalCount());
    assertEquals(3, resultListeners.size());
    // results should only contain listeners with set flowNodeInstanceId == 1L
    final ListenerDto actual0 = resultListeners.get(0);
    assertEquals("21", actual0.getListenerKey());
    assertEquals(ListenerType.TASK_LISTENER, actual0.getListenerType());
    assertEquals(ListenerEventType.UPDATING, actual0.getEvent());
    assertEquals(ListenerState.ACTIVE, actual0.getState());
    assertNull(actual0.getTime());

    final ListenerDto actual1 = resultListeners.get(1);
    assertEquals("12", actual1.getListenerKey());
    assertEquals(ListenerType.EXECUTION_LISTENER, actual1.getListenerType());
    assertEquals(ListenerEventType.END, actual1.getEvent());
    assertEquals(ListenerState.COMPLETED, actual1.getState());
    assertEquals("test_type", actual1.getJobType());
    assertNotNull(actual1.getTime());

    final ListenerDto actual2 = resultListeners.get(2);
    assertEquals("11", actual2.getListenerKey());
    assertEquals(ListenerType.EXECUTION_LISTENER, actual2.getListenerType());
    assertEquals(ListenerEventType.START, actual2.getEvent());
    assertEquals(ListenerState.COMPLETED, actual2.getState());
    assertNotNull(actual2.getTime());
  }

  @Test
  public void testListenerReaderPaging() throws Exception {
    Mockito.when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId(DEFAULT_USER));

    final ListenerRequestDto request1 =
        new ListenerRequestDto().setPageSize(3).setFlowNodeId("test_task");
    final ListenerResponseDto response1 = postListenerRequest("111", request1);
    final List<ListenerDto> resultListeners1 = response1.getListeners();

    assertEquals(5L, response1.getTotalCount());
    assertEquals(3, resultListeners1.size());

    assertEquals("22", resultListeners1.get(0).getListenerKey());
    assertEquals("21", resultListeners1.get(1).getListenerKey());
    assertEquals("12", resultListeners1.get(2).getListenerKey());

    // next page - test searchAfter
    final SortValuesWrapper[] sortValuesAfter = resultListeners1.get(2).getSortValues();
    final ListenerRequestDto request2 =
        new ListenerRequestDto()
            .setPageSize(3)
            .setFlowNodeId("test_task")
            .setSearchAfter(sortValuesAfter);
    final ListenerResponseDto response2 = postListenerRequest("111", request2);
    final List<ListenerDto> resultListeners2 = response2.getListeners();

    assertEquals(5L, response2.getTotalCount());
    assertEquals(2, resultListeners2.size());

    assertEquals("11", resultListeners2.get(0).getListenerKey());
    assertEquals("31", resultListeners2.get(1).getListenerKey());

    // test searchBefore (from last result)
    final SortValuesWrapper[] sortValuesBefore = resultListeners2.get(1).getSortValues();
    final ListenerRequestDto request3 =
        new ListenerRequestDto()
            .setPageSize(3)
            .setFlowNodeId("test_task")
            .setSearchBefore(sortValuesBefore);
    final ListenerResponseDto response3 = postListenerRequest("111", request3);
    final List<ListenerDto> resultListeners3 = response3.getListeners();

    assertEquals(5L, response3.getTotalCount());
    assertEquals(3, resultListeners3.size());

    assertEquals("21", resultListeners3.get(0).getListenerKey());
    assertEquals("12", resultListeners3.get(1).getListenerKey());
    assertEquals("11", resultListeners3.get(2).getListenerKey());
  }

  @Test
  public void testListenerReaderWithTypeFilters() throws Exception {
    Mockito.when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId(DEFAULT_USER));

    // request only Execution Listeners
    final ListenerRequestDto elRequest =
        new ListenerRequestDto()
            .setPageSize(20)
            .setFlowNodeId("test_task")
            .setListenerTypeFilter(ListenerType.EXECUTION_LISTENER);
    final ListenerResponseDto elResponse = postListenerRequest("111", elRequest);
    final List<ListenerDto> elResultListeners = elResponse.getListeners();

    assertEquals(2L, elResponse.getTotalCount());
    assertEquals(2, elResultListeners.size());

    final ListenerDto actual0 = elResultListeners.get(0);
    assertEquals("12", actual0.getListenerKey());
    assertEquals(ListenerType.EXECUTION_LISTENER, actual0.getListenerType());

    final ListenerDto actual1 = elResultListeners.get(1);
    assertEquals("11", actual1.getListenerKey());
    assertEquals(ListenerType.EXECUTION_LISTENER, actual1.getListenerType());

    // Request only Task Listeners
    final ListenerRequestDto tlRequest =
        new ListenerRequestDto()
            .setPageSize(20)
            .setFlowNodeId("test_task")
            .setListenerTypeFilter(ListenerType.TASK_LISTENER);
    final ListenerResponseDto tlResponse = postListenerRequest("111", tlRequest);
    final List<ListenerDto> tlResultListeners = tlResponse.getListeners();

    assertEquals(3L, tlResponse.getTotalCount());
    assertEquals(3, tlResultListeners.size());

    final ListenerDto actual4 = tlResultListeners.get(0);
    assertEquals("22", actual4.getListenerKey());
    assertEquals(ListenerType.TASK_LISTENER, actual4.getListenerType());

    final ListenerDto actual3 = tlResultListeners.get(1);
    assertEquals("21", actual3.getListenerKey());
    assertEquals(ListenerType.TASK_LISTENER, actual3.getListenerType());

    final ListenerDto actual2 = tlResultListeners.get(2);
    assertEquals("31", actual2.getListenerKey());
    assertEquals(ListenerType.TASK_LISTENER, actual2.getListenerType());
  }

  private void createData() throws IOException {

    final JobEntity e1 =
        createJob()
            .setId("11")
            .setKey(11L)
            .setProcessInstanceKey(111L)
            .setFlowNodeInstanceId(1L)
            .setState("COMPLETED")
            .setEndTime(OffsetDateTime.now().minusSeconds(2))
            .setJobKind("EXECUTION_LISTENER")
            .setListenerEventType("START");

    final JobEntity e2 =
        createJob()
            .setId("12")
            .setKey(12L)
            .setProcessInstanceKey(111L)
            .setFlowNodeInstanceId(1L)
            .setState("COMPLETED")
            .setEndTime(OffsetDateTime.now())
            .setJobKind("EXECUTION_LISTENER")
            .setListenerEventType("END");

    final JobEntity e3 =
        createJob()
            .setId("21")
            .setKey(21L)
            .setProcessInstanceKey(111L)
            .setFlowNodeInstanceId(1L)
            .setState("CREATED")
            .setJobKind("TASK_LISTENER")
            .setListenerEventType("UPDATING");

    final JobEntity e4 =
        createJob()
            .setId("22")
            .setKey(22L)
            .setProcessInstanceKey(111L)
            .setFlowNodeInstanceId(2L)
            .setState("FAILED")
            .setJobKind("TASK_LISTENER")
            .setListenerEventType("COMPLETING")
            .setErrorCode("0")
            .setErrorMessage("Internal Error");

    final JobEntity e5 =
        createJob()
            .setId("31")
            .setKey(31L)
            .setProcessInstanceKey(111L)
            .setFlowNodeInstanceId(3L)
            .setState("invalid")
            .setEndTime(OffsetDateTime.now().minusMinutes(5))
            .setJobKind("TASK_LISTENER")
            .setListenerEventType("ASSIGNING");

    // Execution Listener of other process instance that should *not* get returned
    final JobEntity e6 =
        createJob()
            .setId("32")
            .setKey(32L)
            .setProcessInstanceKey(222L)
            .setFlowNodeId("other_ID")
            .setState("COMPLETE")
            .setEndTime(OffsetDateTime.now().minusMinutes(4))
            .setJobKind("EXECUTION_LISTENER");

    // non Listener jobs to check that they do *not* get returned
    final JobEntity e7 =
        createJob()
            .setId("41")
            .setKey(41L)
            .setProcessInstanceKey(111L)
            .setState("invalid")
            .setEndTime(OffsetDateTime.now().minusMinutes(7))
            .setJobKind("BPMN_ELEMENT")
            .setListenerEventType("CREATE");

    final JobEntity e8 =
        createJob()
            .setId("42")
            .setKey(42L)
            .setProcessInstanceKey(111L)
            .setState("invalid")
            .setEndTime(OffsetDateTime.now().minusMinutes(7))
            .setListenerEventType("CREATE");

    testSearchRepository.createOrUpdateDocumentFromObject(jobIndexName, e1.getId(), e1);
    testSearchRepository.createOrUpdateDocumentFromObject(jobIndexName, e2.getId(), e2);
    testSearchRepository.createOrUpdateDocumentFromObject(jobIndexName, e3.getId(), e3);
    testSearchRepository.createOrUpdateDocumentFromObject(jobIndexName, e4.getId(), e4);
    testSearchRepository.createOrUpdateDocumentFromObject(jobIndexName, e5.getId(), e5);
    testSearchRepository.createOrUpdateDocumentFromObject(jobIndexName, e6.getId(), e6);
    testSearchRepository.createOrUpdateDocumentFromObject(jobIndexName, e7.getId(), e7);
    testSearchRepository.createOrUpdateDocumentFromObject(jobIndexName, e8.getId(), e8);
  }

  private JobEntity createJob() {
    return new JobEntity()
        .setPartitionId(1)
        .setFlowNodeId("test_task")
        .setTenantId(DEFAULT_USER)
        .setType("test_type")
        .setRetries(2)
        .setListenerEventType("END");
  }

  private ListenerResponseDto postListenerRequest(
      final String processInstanceId, final ListenerRequestDto query) throws Exception {
    final MvcResult mvcResult =
        mockMvcManager.postRequest(
            ProcessInstanceRestService.PROCESS_INSTANCE_URL
                + "/"
                + processInstanceId
                + "/listeners",
            query,
            HttpStatus.SC_OK);
    final String response = mvcResult.getResponse().getContentAsString();
    return objectMapper.readValue(response, ListenerResponseDto.class);
  }
}
