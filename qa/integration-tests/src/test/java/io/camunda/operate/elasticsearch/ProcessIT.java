/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.elasticsearch;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.webapp.rest.ProcessRestService;
import io.camunda.operate.webapp.rest.dto.ProcessGroupDto;
import io.camunda.operate.webapp.rest.dto.ProcessRequestDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests Elasticsearch queries for process.
 */
public class ProcessIT extends OperateAbstractIT {

  private static final String QUERY_PROCESS_GROUPED_URL = ProcessRestService.PROCESS_URL + "/grouped";

  @MockBean
  private PermissionsService permissionsService;

  @Rule
  public SearchTestRule searchTestRule = new SearchTestRule();

  @Test
  public void testProcessesGroupedWithPermissionWhenNotAllowed() throws Exception {
    // given
    String id1 = "111";
    String id2 = "222";
    String id3 = "333";
    String bpmnProcessId1 = "bpmnProcessId1";
    String bpmnProcessId2 = "bpmnProcessId2";
    String bpmnProcessId3 = "bpmnProcessId3";

    final ProcessEntity process1 = new ProcessEntity().setId(id1).setBpmnProcessId(bpmnProcessId1);
    final ProcessEntity process2 = new ProcessEntity().setId(id2).setBpmnProcessId(bpmnProcessId2);
    final ProcessEntity process3 = new ProcessEntity().setId(id3).setBpmnProcessId(bpmnProcessId3);
    searchTestRule.persistNew(process1, process2, process3);

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ)).thenReturn(
        PermissionsService.ResourcesAllowed.withIds(Set.of()));
    MvcResult mvcResult = postRequest(QUERY_PROCESS_GROUPED_URL, new ProcessRequestDto());

    // then
    List<ProcessGroupDto> response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response).isEmpty();
  }

  @Test
  public void testProcessesGroupedWithPermisssionWhenAllowed() throws Exception {
    // given
    String id1 = "111";
    String id2 = "222";
    String id3 = "333";
    String bpmnProcessId1 = "bpmnProcessId1";
    String bpmnProcessId2 = "bpmnProcessId2";
    String bpmnProcessId3 = "bpmnProcessId3";

    final ProcessEntity process1 = new ProcessEntity().setId(id1).setBpmnProcessId(bpmnProcessId1);
    final ProcessEntity process2 = new ProcessEntity().setId(id2).setBpmnProcessId(bpmnProcessId2);
    final ProcessEntity process3 = new ProcessEntity().setId(id3).setBpmnProcessId(bpmnProcessId3);
    searchTestRule.persistNew(process1, process2, process3);

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ)).thenReturn(PermissionsService.ResourcesAllowed.all());
    MvcResult mvcResult = postRequest(QUERY_PROCESS_GROUPED_URL, new ProcessRequestDto());

    // then
    List<ProcessGroupDto> response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response).hasSize(3);
    assertThat(response.stream().map(ProcessGroupDto::getBpmnProcessId).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(bpmnProcessId1, bpmnProcessId2, bpmnProcessId3);
  }

  @Test
  public void testProcessesGroupedWithPermisssionWhenSomeAllowed() throws Exception {
    // given
    String id1 = "111";
    String id2 = "222";
    String id3 = "333";
    String bpmnProcessId1 = "bpmnProcessId1";
    String bpmnProcessId2 = "bpmnProcessId2";
    String bpmnProcessId3 = "bpmnProcessId3";

    final ProcessEntity process1 = new ProcessEntity().setId(id1).setBpmnProcessId(bpmnProcessId1);
    final ProcessEntity process2 = new ProcessEntity().setId(id2).setBpmnProcessId(bpmnProcessId2);
    final ProcessEntity process3 = new ProcessEntity().setId(id3).setBpmnProcessId(bpmnProcessId3);
    searchTestRule.persistNew(process1, process2, process3);

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ)).thenReturn(
        PermissionsService.ResourcesAllowed.withIds(Set.of(bpmnProcessId2)));
    MvcResult mvcResult = postRequest(QUERY_PROCESS_GROUPED_URL, new ProcessRequestDto());

    // then
    List<ProcessGroupDto> response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response).hasSize(1);
    assertThat(response.stream().map(ProcessGroupDto::getBpmnProcessId).collect(Collectors.toList()))
        .containsExactly(bpmnProcessId2);
  }

  @Test
  public void testProcessesGroupedWithTenantId() throws Exception {
    // given
    String id111 = "111";
    String id121 = "121";
    String id112 = "112";
    String id122 = "122";
    String id2 = "222";
    String id3 = "333";
    String bpmnProcessId1 = "bpmnProcessId1";
    String bpmnProcessId2 = "bpmnProcessId2";
    String bpmnProcessId3 = "bpmnProcessId3";
    String tenantId1 = "tenant1";
    String tenantId2 = "tenant2";

    final ProcessEntity process1_1_1 = new ProcessEntity().setId(id111).setVersion(1).setBpmnProcessId(bpmnProcessId1).setTenantId(tenantId1);
    final ProcessEntity process1_2_1 = new ProcessEntity().setId(id121).setVersion(2).setBpmnProcessId(bpmnProcessId1).setTenantId(tenantId1);
    final ProcessEntity process1_1_2 = new ProcessEntity().setId(id112).setVersion(1).setBpmnProcessId(bpmnProcessId1).setTenantId(tenantId2);
    final ProcessEntity process1_2_2 = new ProcessEntity().setId(id122).setVersion(2).setBpmnProcessId(bpmnProcessId1).setTenantId(tenantId2);
    final ProcessEntity process2 = new ProcessEntity().setId(id2).setVersion(1).setBpmnProcessId(bpmnProcessId2).setTenantId(tenantId1);
    final ProcessEntity process3 = new ProcessEntity().setId(id3).setVersion(1).setBpmnProcessId(bpmnProcessId3).setTenantId(tenantId2);
    searchTestRule.persistNew(process1_1_1, process1_2_1, process1_1_2, process1_2_2, process2, process3);
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ)).thenReturn(PermissionsService.ResourcesAllowed.all());

    // when
    MvcResult mvcResult = postRequest(QUERY_PROCESS_GROUPED_URL, new ProcessRequestDto().setTenantId(tenantId1));

    // then
    List<ProcessGroupDto> response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response).hasSize(2);
    assertThat(response.stream().map(ProcessGroupDto::getTenantId).collect(Collectors.toList()))
        .containsOnly(tenantId1);
    assertThat(response).filteredOn(g -> g.getBpmnProcessId().equals(bpmnProcessId1)).flatMap(g -> g.getProcesses())
        .extracting("id").containsExactlyInAnyOrder(id111, id121);

    // when
    mvcResult = postRequest(QUERY_PROCESS_GROUPED_URL, new ProcessRequestDto().setTenantId(tenantId2));

    // then
    response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response).hasSize(2);
    assertThat(response.stream().map(ProcessGroupDto::getTenantId).collect(Collectors.toList()))
        .containsOnly(tenantId2);
    assertThat(response).filteredOn(g -> g.getBpmnProcessId().equals(bpmnProcessId1)).flatMap(g -> g.getProcesses())
        .extracting("id").containsExactlyInAnyOrder(id112, id122);

    // when
    mvcResult = postRequest(QUERY_PROCESS_GROUPED_URL, new ProcessRequestDto().setTenantId(null));

    // then
    response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response).hasSize(4);
    assertThat(response.stream().map(ProcessGroupDto::getTenantId).collect(Collectors.toList()))
        .containsOnly(tenantId1, tenantId2);
    assertThat(response).filteredOn(g -> g.getBpmnProcessId().equals(bpmnProcessId1) && g.getTenantId().equals(tenantId1)).flatMap(g -> g.getProcesses())
        .extracting("id").containsExactlyInAnyOrder(id111, id121);
    assertThat(response).filteredOn(g -> g.getBpmnProcessId().equals(bpmnProcessId1) && g.getTenantId().equals(tenantId2)).flatMap(g -> g.getProcesses())
        .extracting("id").containsExactlyInAnyOrder(id112, id122);

  }
}
