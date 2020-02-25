/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.api.response.ActivateJobsResponse;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.UpgradeTestCase.TestCaseBuilder;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.VersionUtil;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.agrona.IoUtil;
import org.assertj.core.util.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UpgradeTest {

  public static final String PROCESS_ID = "process";
  private static final String CURRENT_VERSION = "current-test";
  private static final String TASK = "task";
  private static final String MESSAGE = "message";
  private static final File SHARED_DATA;
  private static String lastVersion = VersionUtil.getPreviousVersion();

  static {
    final var sharedDataPath =
        Optional.ofNullable(System.getenv("ZEEBE_CI_SHARED_DATA"))
            .map(Paths::get)
            .orElse(Paths.get(System.getProperty("tmpdir", "/tmp"), "shared"));
    SHARED_DATA = sharedDataPath.toAbsolutePath().toFile();
    IoUtil.ensureDirectoryExists(SHARED_DATA, "temporary folder for Docker");
  }

  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder(SHARED_DATA);
  @Rule public ContainerStateRule state = new ContainerStateRule();

  @Rule
  public RuleChain chain =
      RuleChain.outerRule(new Timeout(5, TimeUnit.MINUTES)).around(tmpFolder).around(state);

  @Parameter public String name;

  @Parameter(1)
  public UpgradeTestCase testCase;

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {
            "job",
            scenario()
                .deployWorkflow(jobWorkflow())
                .createInstance()
                .beforeUpgrade(UpgradeTest::activateJob)
                .afterUpgrade(UpgradeTest::completeJob)
                .done()
          },
          {
            "message",
            scenario()
                .deployWorkflow(messageWorkflow())
                .createInstance()
                .beforeUpgrade(UpgradeTest::awaitOpenMessageSubscription)
                .afterUpgrade(UpgradeTest::publishMessage)
                .done()
          },
        });
  }

  @Test
  public void oldGatewayWithNewBroker() {
    // given
    state
        .broker(CURRENT_VERSION, tmpFolder.getRoot().getPath())
        .withStandaloneGateway(lastVersion)
        .start();
    testCase.setUp(state.client());

    // when
    final long key = testCase.runBefore(state);

    // then
    testCase.runAfter(state, key);
    TestUtil.waitUntil(() -> state.hasElementInState(PROCESS_ID, "ELEMENT_COMPLETED"));
  }

  @Test
  public void upgradeWithSnapshot() {
    upgradeZeebe(false);
  }

  @Test
  public void upgradeWithoutSnapshot() {
    upgradeZeebe(true);
  }

  private void upgradeZeebe(final boolean deleteSnapshot) {
    // given
    state.broker(lastVersion, tmpFolder.getRoot().getPath()).start();
    testCase.setUp(state.client());
    final Long key = testCase.runBefore(state);

    // when
    state.close();
    final File snapshot = new File(tmpFolder.getRoot(), "raft-partition/partitions/1/snapshots/");

    assertThat(snapshot).exists();
    if (deleteSnapshot) {
      Files.delete(snapshot);
    }

    // then
    state.broker(CURRENT_VERSION, tmpFolder.getRoot().getPath()).start();
    testCase.runAfter(state, key);

    TestUtil.waitUntil(() -> state.hasElementInState(PROCESS_ID, "ELEMENT_COMPLETED"));
  }

  private static BpmnModelInstance jobWorkflow() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(TASK, t -> t.zeebeTaskType(TASK))
        .endEvent()
        .done();
  }

  private static long activateJob(final ContainerStateRule state) {
    final ActivateJobsResponse jobsResponse =
        state.client().newActivateJobsCommand().jobType(TASK).maxJobsToActivate(1).send().join();

    TestUtil.waitUntil(() -> state.hasElementInState(TASK, "ACTIVATED"));
    return jobsResponse.getJobs().get(0).getKey();
  }

  private static void completeJob(final ContainerStateRule state, final long key) {
    state.client().newCompleteCommand(key).send().join();
  }

  private static BpmnModelInstance messageWorkflow() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .intermediateCatchEvent(
            "catch", b -> b.message(m -> m.name(MESSAGE).zeebeCorrelationKey("key")))
        .endEvent()
        .done();
  }

  private static long awaitOpenMessageSubscription(final ContainerStateRule state) {
    TestUtil.waitUntil(() -> state.hasLogContaining("WORKFLOW_INSTANCE_SUBSCRIPTION", "OPENED"));
    return -1L;
  }

  private static void publishMessage(final ContainerStateRule state, final long key) {
    state
        .client()
        .newPublishMessageCommand()
        .messageName(MESSAGE)
        .correlationKey("123")
        .send()
        .join();

    TestUtil.waitUntil(() -> state.hasMessageInState(MESSAGE, "PUBLISHED"));
  }

  private static TestCaseBuilder scenario() {
    return UpgradeTestCase.builder();
  }
}
