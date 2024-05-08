/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.webapp.rest.ProcessRestService;
import io.camunda.operate.webapp.rest.dto.ProcessGroupDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

/** Tests Elasticsearch queries for process. */
@Deprecated
// TODO remove when GET /api/processes/grouped is removed
public class ProcessOldIT extends OperateAbstractIT {

  private static final String QUERY_PROCESS_GROUPED_URL =
      ProcessRestService.PROCESS_URL + "/grouped";
  @Rule public SearchTestRule elasticsearchTestRule = new SearchTestRule();
  @MockBean private PermissionsService permissionsService;

  @Test
  public void testProcessesGroupedWithPermissionWhenNotAllowed() throws Exception {
    // given
    final String id1 = "111";
    final String id2 = "222";
    final String id3 = "333";
    final String bpmnProcessId1 = "bpmnProcessId1";
    final String bpmnProcessId2 = "bpmnProcessId2";
    final String bpmnProcessId3 = "bpmnProcessId3";

    final ProcessEntity process1 = new ProcessEntity().setId(id1).setBpmnProcessId(bpmnProcessId1);
    final ProcessEntity process2 = new ProcessEntity().setId(id2).setBpmnProcessId(bpmnProcessId2);
    final ProcessEntity process3 = new ProcessEntity().setId(id3).setBpmnProcessId(bpmnProcessId3);
    elasticsearchTestRule.persistNew(process1, process2, process3);

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));
    final MvcResult mvcResult = getRequest(QUERY_PROCESS_GROUPED_URL);

    // then
    final List<ProcessGroupDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).isEmpty();
  }

  @Test
  public void testProcessesGroupedWithPermisssionWhenAllowed() throws Exception {
    // given
    final String id1 = "111";
    final String id2 = "222";
    final String id3 = "333";
    final String bpmnProcessId1 = "bpmnProcessId1";
    final String bpmnProcessId2 = "bpmnProcessId2";
    final String bpmnProcessId3 = "bpmnProcessId3";

    final ProcessEntity process1 = new ProcessEntity().setId(id1).setBpmnProcessId(bpmnProcessId1);
    final ProcessEntity process2 = new ProcessEntity().setId(id2).setBpmnProcessId(bpmnProcessId2);
    final ProcessEntity process3 = new ProcessEntity().setId(id3).setBpmnProcessId(bpmnProcessId3);
    elasticsearchTestRule.persistNew(process1, process2, process3);

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.all());
    final MvcResult mvcResult = getRequest(QUERY_PROCESS_GROUPED_URL);

    // then
    final List<ProcessGroupDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(3);
    assertThat(
            response.stream().map(ProcessGroupDto::getBpmnProcessId).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(bpmnProcessId1, bpmnProcessId2, bpmnProcessId3);
  }

  @Test
  public void testProcessesGroupedWithPermisssionWhenSomeAllowed() throws Exception {
    // given
    final String id1 = "111";
    final String id2 = "222";
    final String id3 = "333";
    final String bpmnProcessId1 = "bpmnProcessId1";
    final String bpmnProcessId2 = "bpmnProcessId2";
    final String bpmnProcessId3 = "bpmnProcessId3";

    final ProcessEntity process1 = new ProcessEntity().setId(id1).setBpmnProcessId(bpmnProcessId1);
    final ProcessEntity process2 = new ProcessEntity().setId(id2).setBpmnProcessId(bpmnProcessId2);
    final ProcessEntity process3 = new ProcessEntity().setId(id3).setBpmnProcessId(bpmnProcessId3);
    elasticsearchTestRule.persistNew(process1, process2, process3);

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of(bpmnProcessId2)));
    final MvcResult mvcResult = getRequest(QUERY_PROCESS_GROUPED_URL);

    // then
    final List<ProcessGroupDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(1);
    assertThat(
            response.stream().map(ProcessGroupDto::getBpmnProcessId).collect(Collectors.toList()))
        .containsExactly(bpmnProcessId2);
  }
}
