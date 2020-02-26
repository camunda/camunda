/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.ActivateJobsResponse;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.VersionUtil;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
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

  private static final String CURRENT_VERSION = "current-test";
  private static final String PROCESS_ID = "process";
  private static final String TASK = "task";
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
  public TestCase testCase;

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {
            "job",
            scenario()
                .createInstance(jobWorkflow())
                .beforeUpgrade(activateJob())
                .afterUpgrade(completeJob())
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
    testCase.createInstance().accept(state.client());

    // when
    final long key = testCase.before().apply(state);

    // then
    testCase.after().accept(state, key);
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
    testCase.createInstance().accept(state.client());
    final Long key = testCase.before().apply(state);

    // when
    state.close();
    final File snapshot = new File(tmpFolder.getRoot(), "raft-partition/partitions/1/snapshots/");

    assertThat(snapshot).exists();
    if (deleteSnapshot) {
      Files.delete(snapshot);
    }

    // then
    state.broker(CURRENT_VERSION, tmpFolder.getRoot().getPath()).start();
    testCase.after().accept(state, key);

    TestUtil.waitUntil(() -> state.hasElementInState(PROCESS_ID, "ELEMENT_COMPLETED"));
  }

  private static BpmnModelInstance jobWorkflow() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(TASK, t -> t.zeebeTaskType(TASK))
        .endEvent()
        .done();
  }

  private static Function<ContainerStateRule, Long> activateJob() {
    return (ContainerStateRule state) -> {
      final ActivateJobsResponse jobsResponse =
          state.client().newActivateJobsCommand().jobType(TASK).maxJobsToActivate(1).send().join();

      TestUtil.waitUntil(() -> state.hasElementInState(TASK, "ACTIVATED"));
      return jobsResponse.getJobs().get(0).getKey();
    };
  }

  private static BiConsumer<ContainerStateRule, Long> completeJob() {
    return (ContainerStateRule state, Long key) ->
        state.client().newCompleteCommand(key).send().join();
  }

  private static TestCase scenario() {
    return new TestCase();
  }

  private static class TestCase {
    private Consumer<ZeebeClient> createInstance;
    private Function<ContainerStateRule, Long> before;
    private BiConsumer<ContainerStateRule, Long> after;

    TestCase createInstance(final BpmnModelInstance model) {
      this.createInstance =
          client -> {
            client.newDeployCommand().addWorkflowModel(model, PROCESS_ID + ".bpmn").send().join();
            client
                .newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .send()
                .join();
          };
      return this;
    }

    /**
     * Should make zeebe write records and write to state of the feature being tested (e.g., jobs,
     * messages). The workflow should be left in a waiting state so Zeebe can be restarted and
     * execution can be continued after. Takes the container rule as input and outputs a long which
     * can be used after the upgrade to continue the execution.
     */
    TestCase beforeUpgrade(final Function<ContainerStateRule, Long> func) {
      this.before = func;
      return this;
    }
    /**
     * Should continue the instance after the upgrade in a way that will complete the workflow.
     * Takes the container rule and a long (e.g., a key) as input.
     */
    TestCase afterUpgrade(final BiConsumer<ContainerStateRule, Long> func) {
      this.after = func;
      return this;
    }

    public Consumer<ZeebeClient> createInstance() {
      return createInstance;
    }

    public Function<ContainerStateRule, Long> before() {
      return before;
    }

    public BiConsumer<ContainerStateRule, Long> after() {
      return after;
    }
  }
}
