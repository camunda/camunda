/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.operate.entities.JobEntity;
import io.camunda.operate.entities.ListenerEventType;
import io.camunda.operate.entities.ListenerState;
import io.camunda.operate.entities.ListenerType;
import io.camunda.operate.schema.templates.JobTemplate;
import io.camunda.operate.util.j5templates.MockMvcManager;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.ListenerDto;
import io.camunda.operate.webapp.rest.dto.ListenerRequestDto;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.UserService;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
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
  public void testListenerReader() throws Exception {
    Mockito.when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId(DEFAULT_USER));

    final ListenerRequestDto request =
        new ListenerRequestDto().setPageSize(20).setFlowNodeId("test_task");

    final List<ListenerDto> result = postListenerRequest("111", request);

    assertEquals(5, result.size());
    // results should be ordered by finish date (latest first)
    final ListenerDto actual0 = result.get(0);
    assertEquals("12", actual0.getListenerKey());
    assertEquals(ListenerType.EXECUTION_LISTENER, actual0.getListenerType());
    assertEquals(ListenerEventType.END, actual0.getEvent());
    assertEquals(ListenerState.COMPLETED, actual0.getState());
    assertEquals("test_type", actual0.getJobType());
    assertNotNull(actual0.getTime());

    final ListenerDto actual1 = result.get(1);
    assertEquals("11", actual1.getListenerKey());
    assertEquals(ListenerType.EXECUTION_LISTENER, actual1.getListenerType());
    assertEquals(ListenerEventType.START, actual1.getEvent());
    assertEquals(ListenerState.COMPLETED, actual1.getState());
    assertNotNull(actual1.getTime());

    final ListenerDto actual2 = result.get(2);
    assertEquals("31", actual2.getListenerKey());
    assertEquals(ListenerType.TASK_LISTENER, actual2.getListenerType());
    assertEquals(ListenerEventType.UNSPECIFIED, actual2.getEvent());
    assertEquals(ListenerState.UNKNOWN, actual2.getState());
    assertNotNull(actual2.getTime());

    final ListenerDto actual3 = result.get(3);
    assertEquals("21", actual3.getListenerKey());
    assertEquals(ListenerType.TASK_LISTENER, actual3.getListenerType());
    assertEquals(ListenerEventType.UNSPECIFIED, actual3.getEvent());
    assertEquals(ListenerState.ACTIVE, actual3.getState());
    assertNull(actual3.getTime());

    final ListenerDto actual4 = result.get(4);
    assertEquals("22", actual4.getListenerKey());
    assertEquals(ListenerType.TASK_LISTENER, actual4.getListenerType());
    assertEquals(ListenerEventType.UNSPECIFIED, actual4.getEvent());
    assertEquals(ListenerState.FAILED, actual4.getState());
    assertNull(actual4.getTime());
  }

  protected void createData() throws IOException {

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
            .setFlowNodeInstanceId(2L)
            .setState("CREATED")
            .setJobKind("TASK_LISTENER")
            .setListenerEventType("UPDATE");

    final JobEntity e4 =
        createJob()
            .setId("22")
            .setKey(22L)
            .setProcessInstanceKey(111L)
            .setFlowNodeInstanceId(2L)
            .setState("FAILED")
            .setJobKind("TASK_LISTENER")
            .setListenerEventType("COMPLETE")
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
            .setListenerEventType("CREATE");

    // Execution Listener of other process instance that should *not* get returned
    final JobEntity e6 =
        createJob()
            .setId("32")
            .setKey(32L)
            .setProcessInstanceKey(222L)
            .setFlowNodeId("other_ID")
            .setState("COMPLETE")
            .setEndTime(OffsetDateTime.now().minusMinutes(4))
            .setJobKind("EXECUTION_LISTENER")
            .setListenerEventType("START");

    // non Execution Listener jobs to check that they do *not* get returned
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
            .setJobKind("BPMN_ELEMENT")
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

  private List<ListenerDto> postListenerRequest(
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
    final ListenerDto[] dtos = objectMapper.readValue(response, ListenerDto[].class);
    return Arrays.stream(dtos).toList();
  }
}
