/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es;

import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentResponseDto;
import java.util.ArrayList;
import java.util.List;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.core.type.TypeReference;
import static org.assertj.core.api.Assertions.assertThat;
import static io.camunda.operate.util.TestUtil.createIncident;
import static io.camunda.operate.util.TestUtil.createVariable;
import static io.camunda.operate.util.TestUtil.createProcessInstance;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests retrieval of operation taking into account current user name.
 */
public class OperationReaderIT extends OperateIntegrationTest {

  private static final String QUERY_LIST_VIEW_URL = PROCESS_INSTANCE_URL;

  private static final Long PROCESS_KEY_DEMO_PROCESS = 42L;

  private static String PROCESS_INSTANCE_ID_1;
  private static String PROCESS_INSTANCE_ID_2;
  private static String PROCESS_INSTANCE_ID_3;
  private static final String USER_1 = "user1";
  private static final String USER_2 = "user2";
  private static final String USER_3 = "user3";
  private static final String USER_4 = "user4";
  private static final String VARNAME_1 = "var1";
  private static final String VARNAME_2 = "var2";
  private static final String VARNAME_3 = "var3";
  private static final long INCIDENT_1 = 1;
  private static final long INCIDENT_2 = 2;
  private static final long INCIDENT_3 = 3;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Before
  public void before() {
    super.before();
    createData(PROCESS_KEY_DEMO_PROCESS);
  }

  @Test
  public void testProcessInstanceQuery() throws Exception {
    when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId(USER_1));

    ListViewRequestDto processInstanceQueryDto = TestUtil.createGetAllRunningRequest();
    MvcResult mvcResult = postRequest(queryProcessInstances(), processInstanceQueryDto);
    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    List<ListViewProcessInstanceDto> processInstances = response.getProcessInstances();
    assertThat(processInstances).hasSize(3);
    assertThat(processInstances).filteredOn("id", PROCESS_INSTANCE_ID_1)
        .allMatch(pi -> pi.isHasActiveOperation() == true &&
            pi.getOperations().size() == 2);
    assertThat(processInstances).filteredOn("id", PROCESS_INSTANCE_ID_2)
        .allMatch(pi -> pi.isHasActiveOperation() == true &&
            pi.getOperations().size() == 1);
    assertThat(processInstances).filteredOn("id", PROCESS_INSTANCE_ID_3)
        .allMatch(pi -> pi.isHasActiveOperation() == false &&
            pi.getOperations().size() == 0);
  }

  @Test
  public void testQueryIncidentsByProcessInstanceId() throws Exception {
    when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId(USER_1));

    MvcResult mvcResult = getRequest(queryIncidentsByProcessInstanceId(PROCESS_INSTANCE_ID_1));
    IncidentResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    List<IncidentDto> incidents = response.getIncidents();
    assertThat(incidents).hasSize(3);
    assertThat(incidents).filteredOn("id", INCIDENT_1)
        .allMatch(inc -> inc.isHasActiveOperation() == true &&
            inc.getLastOperation() != null);
    assertThat(incidents).filteredOn("id", INCIDENT_2)
        .allMatch(inc -> inc.isHasActiveOperation() == true &&
            inc.getLastOperation() != null);
    assertThat(incidents).filteredOn("id", INCIDENT_3)
        .allMatch(inc -> inc.isHasActiveOperation() == false &&
            inc.getLastOperation() == null);
  }

  @Test
  public void testGetVariables() throws Exception {
    when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId(USER_3));

    MvcResult mvcResult = getVariables(PROCESS_INSTANCE_ID_2);
    List<VariableDto> variables = mockMvcTestRule.listFromResponse(mvcResult, VariableDto.class);

    assertThat(variables).hasSize(3);

    assertThat(variables).filteredOn("name", VARNAME_1)
        .allMatch(inc -> inc.isHasActiveOperation() == false);
    assertThat(variables).filteredOn("name", VARNAME_2)
        .allMatch(inc -> inc.isHasActiveOperation() == true);
    assertThat(variables).filteredOn("name", VARNAME_3)
        .allMatch(inc -> inc.isHasActiveOperation() == true);
  }

  @Test
  public void testQueryProcessInstanceById() throws Exception {
    when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId(USER_4));

    MvcResult mvcResult = getRequest(queryProcessInstanceById(PROCESS_INSTANCE_ID_3));
    ListViewProcessInstanceDto processInstance = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(processInstance.getOperations()).hasSize(2);
    assertThat(processInstance.isHasActiveOperation()).isTrue();

  }

  private String queryProcessInstances() {
    return QUERY_LIST_VIEW_URL;
  }

  private String queryIncidentsByProcessInstanceId(String processInstanceId) {
    return String.format("%s/%s/incidents", PROCESS_INSTANCE_URL, processInstanceId);
  }

  private MvcResult getVariables(String processInstanceId)
      throws Exception {
    final VariableRequestDto request = new VariableRequestDto()
        .setScopeId(String.valueOf(processInstanceId));
    return mockMvc
        .perform(post(getVariablesURL(processInstanceId))
            .content(mockMvcTestRule.json(request))
            .contentType(mockMvcTestRule.getContentType()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();
  }

  private String getVariablesURL(String processInstanceId) {
    return String.format(PROCESS_INSTANCE_URL + "/%s/variables", processInstanceId);
  }

  private String queryProcessInstanceById(String processInstanceId) {
    return String.format("%s/%s", PROCESS_INSTANCE_URL, processInstanceId);
  }

  /**
   */
  protected void createData(Long processDefinitionKey) {

    List<OperateEntity> entities = new ArrayList<>();

    ProcessInstanceForListViewEntity inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey, true);
    PROCESS_INSTANCE_ID_1 = String.valueOf(inst.getKey());
    entities.add(createIncident(IncidentState.ACTIVE, INCIDENT_1, Long.valueOf(PROCESS_INSTANCE_ID_1), processDefinitionKey));
    entities.add(TestUtil.createOperationEntity(inst.getProcessInstanceKey(), INCIDENT_1, null, USER_1));
    entities.add(createIncident(IncidentState.ACTIVE, INCIDENT_2, Long.valueOf(PROCESS_INSTANCE_ID_1), processDefinitionKey));
    entities.add(TestUtil.createOperationEntity(inst.getProcessInstanceKey(), INCIDENT_2, null, USER_1));
    entities.add(createIncident(IncidentState.ACTIVE, INCIDENT_3, Long.valueOf(PROCESS_INSTANCE_ID_1), processDefinitionKey));
    entities.add(TestUtil.createOperationEntity(inst.getProcessInstanceKey(), INCIDENT_3, null, USER_2));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    long processInstanceKey = inst.getKey();
    PROCESS_INSTANCE_ID_2 = String.valueOf(inst.getKey());
    entities.add(createVariable(processInstanceKey, processInstanceKey, VARNAME_1, "value"));
    entities.add(TestUtil.createOperationEntity(inst.getProcessInstanceKey(), null, VARNAME_1, USER_1));
    entities.add(createVariable(processInstanceKey, processInstanceKey, VARNAME_2, "value"));
    entities.add(TestUtil.createOperationEntity(inst.getProcessInstanceKey(), null, VARNAME_2, USER_3));
    entities.add(createVariable(processInstanceKey, processInstanceKey, VARNAME_3, "value"));
    entities.add(TestUtil.createOperationEntity(inst.getProcessInstanceKey(), null, VARNAME_3, USER_3));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    PROCESS_INSTANCE_ID_3 = String.valueOf(inst.getKey());
    entities.add(TestUtil.createOperationEntity(inst.getProcessInstanceKey(), null, null, USER_4));
    entities.add(TestUtil.createOperationEntity(inst.getProcessInstanceKey(), null, null, OperationState.COMPLETED, USER_4, false));
    entities.add(inst);

    elasticsearchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));

  }

}
