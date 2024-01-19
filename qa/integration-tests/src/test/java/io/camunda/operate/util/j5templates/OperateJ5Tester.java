/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.j5templates;

import io.camunda.operate.util.SearchTestRuleProvider;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Arrays;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 * This is a version of the OperateTester utility specifically for the JUnit 5 integration test templates.
 * This class is intended to be a work in progress and have additional functionality ported over from OperateTester
 * and other legacy test utilities as needed while refactoring integration tests to the new templates
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class OperateJ5Tester {

  private final ZeebeClient zeebeClient;

  @Autowired
  private SearchTestRuleProvider searchTestRuleProvider;

  @Autowired
  private SearchCheckPredicatesHolder searchPredicates;

  public OperateJ5Tester(ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  public Long deployProcessAndWait(String classpathResource) {
    Long processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient,
        null, classpathResource);

    searchTestRuleProvider.processAllRecordsAndWait(searchPredicates.getProcessIsDeployedCheck(),
        processDefinitionKey);

    return processDefinitionKey;
  }

  public Long startProcessAndWait(String bpmnProcessId) {
    return startProcessAndWait(bpmnProcessId, null);
  }

  public Long startProcessAndWait(String bpmnProcessId, String payload) {
    Long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, bpmnProcessId, payload);
    searchTestRuleProvider.processAllRecordsAndWait(searchPredicates.getProcessInstanceExistsCheck(), Arrays.asList(processInstanceKey));

    return processInstanceKey;
  }

  public void refreshSearchIndices() {
    searchTestRuleProvider.refreshSearchIndices();
  }
}
