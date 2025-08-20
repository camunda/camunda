/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.operate.util.j5templates.MockMvcManager;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.ListenerDto;
import io.camunda.operate.webapp.rest.dto.ListenerRequestDto;
import io.camunda.operate.webapp.rest.dto.ListenerResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.entities.JobEntity;
import io.camunda.webapps.schema.entities.listener.ListenerEventType;
import io.camunda.webapps.schema.entities.listener.ListenerState;
import io.camunda.webapps.schema.entities.listener.ListenerType;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

public class ListenerReaderIT extends OperateSearchAbstractIT {

  @Autowired MockMvcManager mockMvcManager;
  @Autowired JobTemplate jobTemplate;
  @MockitoBean ProcessInstanceReader mockProcessInstanceReader;
  @Autowired private CamundaAuthenticationProvider camundaAuthenticationProvider;
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
    when(mockProcessInstanceReader.getProcessInstanceByKey(any()))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId("genericid"));
    final ListenerRequestDto request =
        new ListenerRequestDto().setPageSize(20).setFlowNodeId("test_task");
    final ListenerResponseDto response = postListenerRequest("111", request);
    final List<ListenerDto> resultListeners = response.getListeners();

    assertThat(response.getTotalCount()).isEqualTo(5L);
    assertThat(resultListeners.size()).isEqualTo(5);
    // results should be ordered by finish date (latest first, but no date at the beginning)
    final ListenerDto actual4 = resultListeners.get(0);
    assertThat(actual4.getListenerKey()).isEqualTo("22");
    assertThat(actual4.getListenerType()).isEqualTo(ListenerType.TASK_LISTENER);
    assertThat(actual4.getEvent()).isEqualTo(ListenerEventType.COMPLETING);
    assertThat(actual4.getState()).isEqualTo(ListenerState.FAILED);
    assertThat(actual4.getTime()).isNull();

    final ListenerDto actual3 = resultListeners.get(1);
    assertThat(actual3.getListenerKey()).isEqualTo("21");
    assertThat(actual3.getListenerType()).isEqualTo(ListenerType.TASK_LISTENER);
    assertThat(actual3.getEvent()).isEqualTo(ListenerEventType.UPDATING);
    assertThat(actual3.getState()).isEqualTo(ListenerState.ACTIVE);
    assertThat(actual3.getTime()).isNull();

    final ListenerDto actual0 = resultListeners.get(2);
    assertThat(actual0.getListenerKey()).isEqualTo("12");
    assertThat(actual0.getListenerType()).isEqualTo(ListenerType.EXECUTION_LISTENER);
    assertThat(actual0.getEvent()).isEqualTo(ListenerEventType.END);
    assertThat(actual0.getState()).isEqualTo(ListenerState.COMPLETED);
    assertThat(actual0.getJobType()).isEqualTo("test_type");
    assertThat(actual0.getTime()).isNotNull();

    final ListenerDto actual1 = resultListeners.get(3);
    assertThat(actual1.getListenerKey()).isEqualTo("11");
    assertThat(actual1.getListenerType()).isEqualTo(ListenerType.EXECUTION_LISTENER);
    assertThat(actual1.getEvent()).isEqualTo(ListenerEventType.START);
    assertThat(actual1.getState()).isEqualTo(ListenerState.COMPLETED);
    assertThat(actual1.getTime()).isNotNull();

    final ListenerDto actual2 = resultListeners.get(4);
    assertThat(actual2.getListenerKey()).isEqualTo("31");
    assertThat(actual2.getListenerType()).isEqualTo(ListenerType.TASK_LISTENER);
    assertThat(actual2.getEvent()).isEqualTo(ListenerEventType.ASSIGNING);
    assertThat(actual2.getState()).isEqualTo(ListenerState.UNKNOWN);
    assertThat(actual2.getTime()).isNotNull();
  }

  @Test
  public void testListenerReaderFlowNodeInstanceId() throws Exception {
    when(mockProcessInstanceReader.getProcessInstanceByKey(any()))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId("genericid"));
    final ListenerRequestDto request =
        new ListenerRequestDto().setPageSize(20).setFlowNodeInstanceId(1L);
    final ListenerResponseDto response = postListenerRequest("111", request);
    final List<ListenerDto> resultListeners = response.getListeners();

    assertThat(response.getTotalCount()).isEqualTo(3L);
    assertThat(resultListeners.size()).isEqualTo(3);
    // results should only contain listeners with set flowNodeInstanceId == 1L
    final ListenerDto actual0 = resultListeners.get(0);
    assertThat(actual0.getListenerKey()).isEqualTo("21");
    assertThat(actual0.getListenerType()).isEqualTo(ListenerType.TASK_LISTENER);
    assertThat(actual0.getEvent()).isEqualTo(ListenerEventType.UPDATING);
    assertThat(actual0.getState()).isEqualTo(ListenerState.ACTIVE);
    assertThat(actual0.getTime()).isNull();

    final ListenerDto actual1 = resultListeners.get(1);
    assertThat(actual1.getListenerKey()).isEqualTo("12");
    assertThat(actual1.getListenerType()).isEqualTo(ListenerType.EXECUTION_LISTENER);
    assertThat(actual1.getEvent()).isEqualTo(ListenerEventType.END);
    assertThat(actual1.getState()).isEqualTo(ListenerState.COMPLETED);
    assertThat(actual1.getJobType()).isEqualTo("test_type");
    assertThat(actual1.getTime()).isNotNull();

    final ListenerDto actual2 = resultListeners.get(2);
    assertThat(actual2.getListenerKey()).isEqualTo("11");
    assertThat(actual2.getListenerType()).isEqualTo(ListenerType.EXECUTION_LISTENER);
    assertThat(actual2.getEvent()).isEqualTo(ListenerEventType.START);
    assertThat(actual2.getState()).isEqualTo(ListenerState.COMPLETED);
    assertThat(actual2.getTime()).isNotNull();
  }

  @Test
  public void testListenerReaderPaging() throws Exception {
    when(mockProcessInstanceReader.getProcessInstanceByKey(any()))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId("genericid"));
    final ListenerRequestDto request1 =
        new ListenerRequestDto().setPageSize(3).setFlowNodeId("test_task");
    final ListenerResponseDto response1 = postListenerRequest("111", request1);
    final List<ListenerDto> resultListeners1 = response1.getListeners();

    assertThat(response1.getTotalCount()).isEqualTo(5L);
    assertThat(resultListeners1.size()).isEqualTo(3);

    assertThat(resultListeners1.get(0).getListenerKey()).isEqualTo("22");
    assertThat(resultListeners1.get(1).getListenerKey()).isEqualTo("21");
    assertThat(resultListeners1.get(2).getListenerKey()).isEqualTo("12");

    // next page - test searchAfter
    final SortValuesWrapper[] sortValuesAfter = resultListeners1.get(2).getSortValues();
    final ListenerRequestDto request2 =
        new ListenerRequestDto()
            .setPageSize(3)
            .setFlowNodeId("test_task")
            .setSearchAfter(sortValuesAfter);
    final ListenerResponseDto response2 = postListenerRequest("111", request2);
    final List<ListenerDto> resultListeners2 = response2.getListeners();

    assertThat(response2.getTotalCount()).isEqualTo(5L);
    assertThat(resultListeners2.size()).isEqualTo(2);

    assertThat(resultListeners2.get(0).getListenerKey()).isEqualTo("11");
    assertThat(resultListeners2.get(1).getListenerKey()).isEqualTo("31");

    // test searchBefore (from last result)
    final SortValuesWrapper[] sortValuesBefore = resultListeners2.get(1).getSortValues();
    final ListenerRequestDto request3 =
        new ListenerRequestDto()
            .setPageSize(3)
            .setFlowNodeId("test_task")
            .setSearchBefore(sortValuesBefore);
    final ListenerResponseDto response3 = postListenerRequest("111", request3);
    final List<ListenerDto> resultListeners3 = response3.getListeners();

    assertThat(response3.getTotalCount()).isEqualTo(5L);
    assertThat(resultListeners3.size()).isEqualTo(3);

    assertThat(resultListeners3.get(0).getListenerKey()).isEqualTo("21");
    assertThat(resultListeners3.get(1).getListenerKey()).isEqualTo("12");
    assertThat(resultListeners3.get(2).getListenerKey()).isEqualTo("11");
  }

  @Test
  public void testListenerReaderWithTypeFilters() throws Exception {
    when(mockProcessInstanceReader.getProcessInstanceByKey(any()))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId("genericid"));
    // request only Execution Listeners
    final ListenerRequestDto elRequest =
        new ListenerRequestDto()
            .setPageSize(20)
            .setFlowNodeId("test_task")
            .setListenerTypeFilter(ListenerType.EXECUTION_LISTENER);
    final ListenerResponseDto elResponse = postListenerRequest("111", elRequest);
    final List<ListenerDto> elResultListeners = elResponse.getListeners();

    assertThat(elResponse.getTotalCount()).isEqualTo(2L);
    assertThat(elResultListeners.size()).isEqualTo(2);

    final ListenerDto actual0 = elResultListeners.get(0);
    assertThat(actual0.getListenerKey()).isEqualTo("12");
    assertThat(actual0.getListenerType()).isEqualTo(ListenerType.EXECUTION_LISTENER);

    final ListenerDto actual1 = elResultListeners.get(1);
    assertThat(actual1.getListenerKey()).isEqualTo("11");
    assertThat(actual1.getListenerType()).isEqualTo(ListenerType.EXECUTION_LISTENER);

    // Request only Task Listeners
    final ListenerRequestDto tlRequest =
        new ListenerRequestDto()
            .setPageSize(20)
            .setFlowNodeId("test_task")
            .setListenerTypeFilter(ListenerType.TASK_LISTENER);
    final ListenerResponseDto tlResponse = postListenerRequest("111", tlRequest);
    final List<ListenerDto> tlResultListeners = tlResponse.getListeners();

    assertThat(tlResponse.getTotalCount()).isEqualTo(3L);
    assertThat(tlResultListeners.size()).isEqualTo(3);

    final ListenerDto actual4 = tlResultListeners.get(0);
    assertThat(actual4.getListenerKey()).isEqualTo("22");
    assertThat(actual4.getListenerType()).isEqualTo(ListenerType.TASK_LISTENER);

    final ListenerDto actual3 = tlResultListeners.get(1);
    assertThat(actual3.getListenerKey()).isEqualTo("21");
    assertThat(actual3.getListenerType()).isEqualTo(ListenerType.TASK_LISTENER);

    final ListenerDto actual2 = tlResultListeners.get(2);
    assertThat(actual2.getListenerKey()).isEqualTo("31");
    assertThat(actual2.getListenerType()).isEqualTo(ListenerType.TASK_LISTENER);
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
