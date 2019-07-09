/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import io.zeebe.client.api.worker.JobWorker;
import java.util.function.Predicate;
import org.camunda.operate.zeebeimport.ImportPositionHolder;
import org.camunda.operate.zeebeimport.PartitionHolder;
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
import org.junit.Rule;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.ClientRule;
import io.zeebe.test.EmbeddedBrokerRule;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class OperateZeebeIntegrationTest extends OperateIntegrationTest {

  @MockBean
  protected ZeebeClient mockedZeebeClient;    //we don't want to create ZeebeClient, we will rather use the one from test rule

  @Rule
  public final OperateZeebeRule zeebeRule;

  protected ClientRule clientRule;

  protected EmbeddedBrokerRule brokerRule;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  private PartitionHolder partitionHolder;

  @Autowired
  private ImportPositionHolder importPositionHolder;

  @Autowired
  private WorkflowCache workflowCache;

  @Autowired
  @Qualifier("workflowIsDeployedCheck")
  private Predicate<Object[]> workflowIsDeployedCheck;

  @Autowired
  @Qualifier("incidentIsActiveCheck")
  private Predicate<Object[]> incidentIsActiveCheck;

  @Autowired
  @Qualifier("workflowInstanceIsCanceledCheck")
  private Predicate<Object[]> workflowInstanceIsCanceledCheck;

  @Autowired
  @Qualifier("activityIsCompletedCheck")
  private Predicate<Object[]> activityIsCompletedCheck;

  private JobWorker jobWorker;

  private String workerName;

  protected void before() {
    clientRule = zeebeRule.getClientRule();
    assertThat(clientRule).as("clientRule is not null").isNotNull();
    brokerRule = zeebeRule.getBrokerRule();
    assertThat(brokerRule).as("brokerRule is not null").isNotNull();

    workerName = TestUtil.createRandomString(10);

    workflowCache.clearCache();
    importPositionHolder.clearCache();
    try {
      FieldSetter.setField(partitionHolder, PartitionHolder.class.getDeclaredField("zeebeClient"), getClient());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }
  }

  protected void after() {
    workflowCache.clearCache();
    importPositionHolder.clearCache();
    if (jobWorker != null && jobWorker.isOpen()) {
      jobWorker.close();
      jobWorker = null;
    }
  }

  public OperateZeebeIntegrationTest() {
    this(EmbeddedBrokerRule.DEFAULT_CONFIG_FILE);
  }

  public OperateZeebeIntegrationTest(
    final String configFileClasspathLocation) {
    zeebeRule = new OperateZeebeRule(configFileClasspathLocation);

  }

  public ZeebeClient getClient() {
    return clientRule.getClient();
  }

  public BrokerCfg getBrokerCfg() {
    return brokerRule.getBrokerCfg();
  }

  public String getWorkerName() {
    return workerName;
  }

  public JobWorker getJobWorker() {
    return jobWorker;
  }

  public void setJobWorker(JobWorker jobWorker) {
    this.jobWorker = jobWorker;
  }

  public Long failTaskWithNoRetriesLeft(String taskName, long workflowInstanceKey, String errorMessage) {
    Long jobKey = ZeebeTestUtil.failTask(getClient(), taskName, getWorkerName(), 3, errorMessage);
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);
    return jobKey;
  }

  protected Long deployWorkflow(String... classpathResources) {
    final Long workflowKey = ZeebeTestUtil.deployWorkflow(getClient(), classpathResources);
    elasticsearchTestRule.processAllRecordsAndWait(workflowIsDeployedCheck, workflowKey);
    return workflowKey;
  }

  protected Long deployWorkflow(BpmnModelInstance workflow, String resourceName) {
    final Long workflowId = ZeebeTestUtil.deployWorkflow(getClient(), workflow, resourceName);
    elasticsearchTestRule.processAllRecordsAndWait(workflowIsDeployedCheck, workflowId);
    return workflowId;
  }

  protected void cancelWorkflowInstance(long workflowInstanceKey) {
    ZeebeTestUtil.cancelWorkflowInstance(getClient(), workflowInstanceKey);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);
  }

  protected void completeTask(long workflowInstanceKey, String activityId, String payload) {
    JobWorker jobWorker = ZeebeTestUtil.completeTask(getClient(), activityId, getWorkerName(), payload);
    elasticsearchTestRule.processAllRecordsAndWait(activityIsCompletedCheck, workflowInstanceKey, activityId);
    jobWorker.close();
  }
}
