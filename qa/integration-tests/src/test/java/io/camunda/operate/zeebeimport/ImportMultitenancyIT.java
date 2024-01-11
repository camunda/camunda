/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.OperateProfileService;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.IdentityTester;
import io.camunda.operate.util.IdentityOperateZeebeAbstractIT;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.security.tenant.TenantService;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;
import static io.camunda.operate.qa.util.TestContainerUtil.TENANT_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = { TestApplication.class},
    properties = { OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
        OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
        "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
        OperateProperties.PREFIX + ".multiTenancy.enabled = true"})
@ActiveProfiles({ OperateProfileService.IDENTITY_AUTH_PROFILE, "test" })
public class ImportMultitenancyIT extends IdentityOperateZeebeAbstractIT {

  @Autowired
  private ListViewReader listViewReader;

  private String defaultTenantId = "<default>";

  @DynamicPropertySource
  protected static void registerProperties(DynamicPropertyRegistry registry) {
    IdentityTester.registerProperties(registry, true);
  }

  @BeforeClass
  public static void beforeClass() {
    IdentityTester.startIdentityBeforeTestClass(true);
  }

  @Test
  public void testTenantIsAssignedAndImported() {
    doReturn(TenantService.AuthenticatedTenants.assignedTenants(List.of(TENANT_1))).when(tenantService)
        .getAuthenticatedTenants();

    // having
    String processId = "demoProcess";
    final Long processDefinitionKey = deployProcessWithTenant(TENANT_1, "demoProcess_v_1.bpmn");

    //when
    final Long processInstanceKey = tester.startProcessInstance(processId, null, "{\"a\": \"b\"}", TENANT_1)
        .getProcessInstanceKey();
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskA");
    searchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, "a");

    //then
    //assert process instance
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getTenantId()).isEqualTo(TENANT_1);
    //assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).extracting("tenantId").containsOnly(TENANT_1);
  }

  private ListViewProcessInstanceDto getSingleProcessInstanceForListView(ListViewRequestDto request) {
    final ListViewResponseDto listViewResponse = listViewReader.queryProcessInstances(request);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(1);
    assertThat(listViewResponse.getProcessInstances()).hasSize(1);
    return listViewResponse.getProcessInstances().get(0);
  }

  private ListViewProcessInstanceDto getSingleProcessInstanceForListView() {
    return getSingleProcessInstanceForListView(createGetAllProcessInstancesRequest());
  }

}
