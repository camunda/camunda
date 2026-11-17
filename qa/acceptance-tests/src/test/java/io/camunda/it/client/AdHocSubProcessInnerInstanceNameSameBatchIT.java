/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pins that an ad-hoc sub-process inner instance keeps the name resolved from its entry element
 * even when the entry element's activation and the inner instance's completion are written by the
 * same exporter flush batch.
 *
 * <p>On Elasticsearch/OpenSearch this reproduces the production bug (#58803): if two exporter
 * handlers touch the same cached entity within one flush batch — {@code
 * FlowNodeInstanceNameFromAdHocActivityHandler}, which resolves an ad-hoc inner instance's name,
 * and {@code FlowNodeInstanceFromProcessInstanceHandler}, which always nulls that field back out
 * for the inner instance's own lifecycle records — the second handler's null-write can clobber the
 * first handler's resolved name right before its {@code flush()} runs, throwing a {@code
 * NullPointerException}. See {@code ExporterBatchWriterFlowNodeInstanceNameClobberTest}
 * (camunda-exporter module) for this mechanism pinned in isolation; this test additionally proves
 * the fix survives the real broker -&gt; exporter -&gt; search-index path.
 *
 * <p>On RDBMS the write path has no equivalent clobber: the name write merges into the inner
 * instance's still-queued insert, and where it cannot merge, the flush orders the set-if-null
 * {@code updateNameIfNull} statement after every other statement of the batch that writes {@code
 * FLOW_NODE_NAME} — {@code insert} (all inserts precede all updates) and the full-row {@code
 * update}. Only {@code updateStateAndEndDate} sorts after it, and it writes disjoint columns. There
 * the test is a parity regression pin over the same condition rather than a reproduction — it is
 * expected to pass on arrival and to fail only if the RDBMS write path starts dropping an
 * already-resolved name.
 *
 * <p>{@code @MultiDbTest}'s managed defaults flush after every record ({@code bulk.size=1} on
 * ES/OS, a zero flush interval on RDBMS), so two records can never share a batch. This test instead
 * runs its own {@link TestStandaloneBroker} configured to batch generously on whichever secondary
 * storage is active, so the entry child's activation and the inner instance's completion have room
 * to land in the same flush batch — the condition this bug requires, and which the sibling {@code
 * AdHocSubProcessInnerInstanceNameIT} structurally cannot reach.
 */
@MultiDbTest
public class AdHocSubProcessInnerInstanceNameSameBatchIT {

  @MultiDbTestApplication(managedLifecycle = false)
  private static final TestStandaloneBroker STANDALONE_CAMUNDA = new TestStandaloneBroker();

  @BeforeAll
  static void setUp() {
    STANDALONE_CAMUNDA.withUnifiedConfig(
        c -> {
          // See class javadoc for why batching is what makes this test meaningful. The knobs are
          // per-storage: getDocumentBasedDatabase() returns null on RDBMS, and each storage
          // has its own "flush after every record" default that has to be lifted.
          final var secondaryStorage = c.getData().getSecondaryStorage();
          if (secondaryStorage.getType().isRdbms()) {
            final var rdbms = secondaryStorage.getRdbms();
            // Both matter: the exporter flushes per record if either the interval is zero or the
            // queue size is non-positive.
            rdbms.setQueueSize(5000);
            rdbms.setFlushInterval(Duration.ofSeconds(5));
          } else {
            final var bulk = secondaryStorage.getDocumentBasedDatabase().getBulk();
            bulk.setSize(5000);
            bulk.setDelay(Duration.ofSeconds(5));
          }
        });

    STANDALONE_CAMUNDA.start();
    STANDALONE_CAMUNDA.awaitCompleteTopology();
  }

  @AfterAll
  static void tearDown() {
    STANDALONE_CAMUNDA.stop();
  }

  @Test
  void shouldRetainNameWhenEntryElementCompletesInSameBatch() {
    try (final var camundaClient = STANDALONE_CAMUNDA.newClientBuilder().build()) {
      // given - an ad-hoc subprocess whose entry element is a plain/undefined task: no job type
      // and no outgoing sequence flow, so it auto-completes immediately once activated, which in
      // turn immediately completes the inner instance in the same exporter flush batch.
      final var processModel =
          Bpmn.createExecutableProcess("adHocInnerInstanceNameSameBatchProcess")
              .startEvent()
              .adHocSubProcess(
                  "adHocSubProcess",
                  ahsp -> ahsp.task("autoCompleteTask").name("Auto complete task"))
              .endEvent("end")
              .done();

      final var process =
          deployProcessAndWaitForIt(
              camundaClient, processModel, "adhoc-inner-instance-name-same-batch.bpmn");

      // when
      final long processInstanceKey =
          startProcessInstance(camundaClient, process.getBpmnProcessId()).getProcessInstanceKey();

      waitForElementInstances(
          camundaClient,
          f -> f.elementId("adHocSubProcess").processInstanceKey(processInstanceKey),
          1);

      final long adHocSubProcessInstanceKey =
          camundaClient
              .newElementInstanceSearchRequest()
              .filter(f -> f.elementId("adHocSubProcess").processInstanceKey(processInstanceKey))
              .execute()
              .items()
              .getFirst()
              .getElementInstanceKey();

      camundaClient
          .newActivateAdHocSubProcessActivitiesCommand(String.valueOf(adHocSubProcessInstanceKey))
          .activateElements("autoCompleteTask")
          .send()
          .join();

      // then - the entry element auto-completes and the ad-hoc subprocess has no active elements
      // left, so the inner instance completes right away. With the bug present, the exporter
      // throws internally and gets stuck retrying the batch, so COMPLETED never becomes visible
      // within the await window.
      Awaitility.await("inner instance completes")
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  assertThat(
                          queryInnerInstance(
                                  camundaClient,
                                  processInstanceKey,
                                  "adHocSubProcess#innerInstance")
                              .getState())
                      .isEqualTo(ElementInstanceState.COMPLETED));

      final var completedInnerInstance =
          queryInnerInstance(camundaClient, processInstanceKey, "adHocSubProcess#innerInstance");
      assertThat(completedInnerInstance.getElementName())
          .describedAs(
              "inner instance should be named after the activated entry element even though "
                  + "activation and completion landed in the same exporter flush batch")
          .isEqualTo("Auto complete task");
      assertThat(completedInnerInstance.getElementName())
          .describedAs("inner instance name should not fall back to the synthetic id")
          .isNotEqualTo("adHocSubProcess#innerInstance");
    }
  }

  private static ElementInstance queryInnerInstance(
      final CamundaClient camundaClient,
      final long processInstanceKey,
      final String innerInstanceElementId) {
    return camundaClient
        .newElementInstanceSearchRequest()
        .filter(f -> f.elementId(innerInstanceElementId).processInstanceKey(processInstanceKey))
        .execute()
        .items()
        .getFirst();
  }
}
