/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessInstanceReaderIT extends OperateZeebeIntegrationTest {

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  private Long processInstanceKey;

  private Random random = new Random();

  @Before
  public void setUp() {
    // Given
    processInstanceKey = tester.deployProcess("demoProcess_v_1.bpmn").waitUntil().processIsDeployed()
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .and().failTask("taskA", "Some error").waitUntil().incidentIsActive()
        .getProcessInstanceKey();
  }

  // Case: Use process instance key ( default case )
  @Test
  public void testGetProcessInstanceWithOperationsByKeyWithCorrectKey() {
    // When
    ListViewProcessInstanceDto processInstance = processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    assertThat(processInstance.getId()).isEqualTo(processInstanceKey.toString());
  }

  @Test
  public void testGetProcessInstanceWithCorrectKey() {
    // When
    ProcessInstanceForListViewEntity processInstance = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstance.getId()).isEqualTo(processInstanceKey.toString());
  }

  // OPE-667
  // Case: Use (accidently) activity key for process instance key
  @Test(expected = NotFoundException.class)
  public void testGetProcessInstanceWithOperationsByKeyWithActivityKey() {
    // get a random activity id
    List<FlowNodeInstanceEntity> activities = tester.getAllFlowNodeInstances(processInstanceKey);
    Long activityId = activities.get(random.nextInt(activities.size())).getKey();
    // When
    processInstanceReader.getProcessInstanceWithOperationsByKey(activityId);
    // then throw NotFoundException
  }

  // Case: Use random key for process instance key
  @Test(expected = NotFoundException.class)
  public void testGetProcessInstanceWithOperationsByKeyWithRandomKey() {
    // get a random
    Long key = random.nextLong();
    while (key.equals(processInstanceKey)) {
      key = random.nextLong();
    }
    // When
    processInstanceReader.getProcessInstanceWithOperationsByKey(key);
    // then throw NotFoundException
  }

}
