/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.response.Incident;
import io.camunda.it.utils.BrokerITInvocationProvider;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

public class IncidentExporterIT {

  @RegisterExtension
  static final BrokerITInvocationProvider PROVIDER = new BrokerITInvocationProvider();

  @TestTemplate
  void shouldExportIncident(final TestStandaloneBroker testBroker) {
    final var steps = new IncidentSteps(testBroker);

    // given
    steps.deployProcessFromClasspath("process/error-end-event.bpmn");
    // when
    steps.startProcessInstance();

    // then
    steps
        .incidentExistsForProcessInstance(
            i -> {
              assertThat(i.getErrorType()).isEqualTo(ErrorType.UNHANDLED_ERROR_EVENT.name());
            })
        .processInstanceHasIncident();
  }

  @TestTemplate
  void shouldExportAndResolveNestedIncidents(final TestStandaloneBroker testBroker) {
    final var steps = new IncidentSteps(testBroker);

    // ----------------------------
    // First create the incidents
    // ----------------------------

    // given
    steps
        .deployProcessFromClasspath("process/nestedErrorParentProcess.bpmn")
        .deployProcessFromClasspath("process/nestedErrorChildProcess.bpmn")
        .startProcessInstance("nestedErrorParentProcess")
        .waitForChildProcessInstance();

    // when
    steps.throwIncident("errorTask", "this-errorcode-does-not-exists", "Process error");

    // then
    steps
        .incidentExistsForProcessInstance(
            steps.processInstanceKey,
            i -> {
              assertThat(i.getErrorType()).isEqualTo(ErrorType.UNHANDLED_ERROR_EVENT.name());
              assertThat(i.getFlowNodeId()).isEqualTo("parentprocess_error_task");
            })
        .incidentExistsForProcessInstance(
            steps.childProcessInstanceKey,
            i -> {
              assertThat(i.getErrorType()).isEqualTo(ErrorType.UNHANDLED_ERROR_EVENT.name());
              assertThat(i.getFlowNodeId()).isEqualTo("childprocess_error_task");
            })
        .processInstanceHasIncident(steps.processInstanceKey)
        .processInstanceHasIncident(steps.childProcessInstanceKey)
        .flowNodeHasIncident("call_error_subprocess", false)
        .flowNodeHasIncident("parentprocess_error_task", true)
        .flowNodeHasIncident("childprocess_error_task", true);

    // ----------------------------
    // Now solve the incidents from child to parent
    // ----------------------------

    // when
    steps.resolveIncidentForProcessInstance(steps.childProcessInstanceKey);

    // then
    steps
        .incidentExistsForProcessInstance(
            steps.childProcessInstanceKey,
            i -> {
              assertThat(i.getState()).isEqualTo("RESOLVED");
            })
        .processInstanceHasNoIncident(steps.childProcessInstanceKey)
        .processInstanceHasIncident(steps.processInstanceKey)
        .flowNodeHasNoIncident("call_error_subprocess")
        .flowNodeHasNoIncident("childprocess_error_task")
        .flowNodeHasIncident("parentprocess_error_task", true);

    // when
    steps.resolveIncidentForProcessInstance(steps.processInstanceKey);

    // then
    steps
        .incidentExistsForProcessInstance(
            steps.processInstanceKey,
            i -> {
              assertThat(i.getState()).isEqualTo("RESOLVED");
            })
        .processInstanceHasNoIncident(steps.childProcessInstanceKey)
        .processInstanceHasNoIncident(steps.processInstanceKey)
        .flowNodeHasNoIncident("parentprocess_error_task");
  }

  static class IncidentSteps extends TestSteps<IncidentSteps> {

    protected long incidentKey;

    public IncidentSteps(final TestStandaloneBroker broker) {
      super(broker);
    }

    // ----------------------
    // GIVEN / WHEN
    // ----------------------

    public IncidentSteps throwIncident(
        final String jobType, final String errorCode, final String errorMessage) {
      client
          .newActivateJobsCommand()
          .jobType(jobType)
          .maxJobsToActivate(10)
          .workerName(UUID.randomUUID().toString())
          .send()
          .join()
          .getJobs()
          .forEach(
              j -> {
                client
                    .newThrowErrorCommand(j.getKey())
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .send()
                    .join();
              });

      return self();
    }

    public IncidentSteps resolveIncidentForProcessInstance(final long processInstanceKey) {
      client
          .newIncidentQuery()
          .filter(f -> f.processInstanceKey(processInstanceKey))
          .send()
          .join()
          .items()
          .forEach(
              i -> {
                client.newResolveIncidentCommand(i.getIncidentKey()).send().join();
              });

      return self();
    }

    // ----------------------
    // THEN
    // ----------------------

    public IncidentSteps incidentExistsForProcessInstance(final Consumer<Incident> assertion) {
      return incidentExistsForProcessInstance(processInstanceKey, assertion);
    }

    public IncidentSteps incidentExistsForProcessInstance(
        final long processInstanceKey, final Consumer<Incident> assertion) {
      incidentsExistAndMatch(
          f -> f.processInstanceKey(processInstanceKey),
          incidents -> {
            assertThat(incidents).hasSize(1);
            assertion.accept(incidents.getFirst());

            incidentKey = incidents.getFirst().getIncidentKey();
          });

      return self();
    }

    public IncidentSteps processInstanceHasIncident() {
      return processInstanceHasIncident(processInstanceKey);
    }

    public IncidentSteps processInstanceHasIncident(final long processInstanceKey) {
      return processInstanceExistAndMatch(
          f -> f.processInstanceKey(processInstanceKey),
          f -> {
            assertThat(f).hasSize(1);
            assertThat(f.getFirst().getHasIncident()).isTrue();
          });
    }

    public IncidentSteps processInstanceHasNoIncident(final long processInstanceKey) {
      return processInstanceExistAndMatch(
          f -> f.processInstanceKey(processInstanceKey),
          f -> {
            assertThat(f).hasSize(1);
            assertThat(f.getFirst().getHasIncident()).isFalse();
          });
    }

    public IncidentSteps flowNodeHasIncident(final String flowNodeId, final boolean withKey) {
      return flowNodeInstanceExistAndMatch(
          f -> f.flowNodeId(flowNodeId),
          f -> {
            assertThat(f).hasSize(1);
            assertThat(f.getFirst().getIncident()).isTrue();
            assertThat(f.getFirst().getIncidentKey() != null).isEqualTo(withKey);
          });
    }

    public IncidentSteps flowNodeHasNoIncident(final String flowNodeId) {
      return flowNodeInstanceExistAndMatch(
          f -> f.flowNodeId(flowNodeId),
          f -> {
            assertThat(f).hasSize(1);
            assertThat(f.getFirst().getIncident()).isFalse();
            assertThat(f.getFirst().getIncidentKey()).isNull();
          });
    }
  }
}
