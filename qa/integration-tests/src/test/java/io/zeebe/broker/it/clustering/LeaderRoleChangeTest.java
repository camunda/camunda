/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.clustering;

public class LeaderRoleChangeTest {
  // TODO THIS IS NOT AN QA TEST

  //
  //  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  //  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);
  //
  //  @ClassRule
  //  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);
  //
  //  @Rule public BrokerClassRuleHelper helper = new BrokerClassRuleHelper();
  //
  //  private String jobType;
  //
  //  @Before
  //  public void init() {
  //    jobType = helper.getJobType();
  //  }
  //
  //  @Test
  //  public void shouldChangeRolesFast() throws InterruptedException {
  //
  //    final int activateJobs = 1;
  //
  //    CLIENT_RULE.createSingleJob(jobType);
  //
  //    final ZeebePartition partitionInstallService =
  //        BROKER_RULE.getService(partitionInstallServiceName(getPartitionName(1)));
  //
  //    partitionInstallService.onTransitionToFollower(1);
  //    partitionInstallService.onTransitionToLeader(1);
  //
  //    partitionInstallService.onTransitionToFollower(1);
  //    partitionInstallService.onTransitionToLeader(1);
  //
  //    // when
  //
  //    final BpmnModelInstance modelInstance =
  //        CLIENT_RULE.createSingleJobModelInstance(jobType, b -> {});
  //    // wait until new role transitions are completed  and the leader starts accepting commands
  // again
  //    waitUntil(() -> deployWorkflow(modelInstance));
  //
  //    final ActivateJobsResponse response =
  //        CLIENT_RULE
  //            .getClient()
  //            .newActivateJobsCommand()
  //            .jobType(jobType)
  //            .maxJobsToActivate(activateJobs)
  //            .send()
  //            .join();
  //
  //    // then
  //    assertThat(response.getJobs()).hasSize(activateJobs);
  //  }
  //
  //  private boolean deployWorkflow(final BpmnModelInstance modelInstance) {
  //    try {
  //      final DeploymentEvent deploymentEvent =
  //          CLIENT_RULE
  //              .getClient()
  //              .newDeployCommand()
  //              .addWorkflowModel(modelInstance, "workflow.bpmn")
  //              .send()
  //              .join();
  //      return deploymentEvent.getWorkflows().get(0).getWorkflowKey() > 0;
  //    } catch (Exception e) {
  //      return false;
  //    }
  //  }
}
