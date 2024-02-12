/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

public class TenantAwareFormLinkingTest {
  private static final String PROCESS_ID = "processId";
  private static final String FORM_ID = "Form_0w7r08e";
  private static final String TEST_FORM = "/form/test-form-1.form";
  private static final String TENANT = "tenant";
  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldActivateUserTask() {
    // when
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask()
                .zeebeFormId(FORM_ID)
                .endEvent()
                .done())
        .withXmlClasspathResource(TEST_FORM)
        .withTenantId(TENANT)
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .limit(2))
        .extracting(r -> r.getValue().getTenantId())
        .containsOnly(TENANT);
  }

  @Test
  public void shouldNotActivateUserTask() {
    // when
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask()
                .zeebeFormId(FORM_ID)
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();
    engine.deployment().withXmlClasspathResource(TEST_FORM).withTenantId("otherTenant").deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT).create();

    // then
    assertThat(
            RecordingExporter.incidentRecords()
                .onlyEvents()
                .withProcessInstanceKey(processInstanceKey)
                .limit(1))
        .extracting(
            r -> r.getValue().getErrorType(),
            r -> r.getValue().getErrorMessage(),
            r -> r.getValue().getTenantId())
        .containsExactly(
            tuple(
                ErrorType.FORM_NOT_FOUND,
                """
                Expected to find a form with id '%s', but no form with this id is found, at least \
                a form with this id should be available. To resolve the Incident please deploy a \
                form with the same id"""
                    .formatted(FORM_ID),
                TENANT));
  }

  @Test
  public void shouldActivateUserTaskWithCorrectFormKey() {
    // when
    final Record<DeploymentRecordValue> deploy =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("form_linking_test")
                    .startEvent()
                    .userTask("A")
                    .zeebeFormId("Form_1mmjkgz")
                    .endEvent()
                    .done())
            .withXmlClasspathResource("/form/form_linkin_test_form_1.form")
            .withXmlClasspathResource("/form/form_linkin_test_form_2.form")
            .deploy();

    final Map<String, Long> formNameToKey = new HashMap<>();

    deploy
        .getValue()
        .getFormMetadata()
        .forEach(
            formMetadataValue -> {
              formNameToKey.put(
                  formMetadataValue.getResourceName(), formMetadataValue.getFormKey());
            });

    final int processInstanceCount = 10;
    for (int i = 1; i <= processInstanceCount; i++) {
      engine.processInstance().ofBpmnProcessId("form_linking_test").create();

      if (i == 5) {
        final Record<DeploymentRecordValue> deploy1 =
            engine
                .deployment()
                .withXmlResource(
                    Bpmn.createExecutableProcess("form_linking_test_2")
                        .startEvent()
                        .userTask("A")
                        .zeebeFormId("Form_1cqi2h1")
                        .endEvent()
                        .done())
                .withXmlClasspathResource("/form/form_linkin_test_form_3.form")
                .deploy();

        engine.processInstance().ofBpmnProcessId("form_linking_test_2").create();

        deploy1
            .getValue()
            .getFormMetadata()
            .forEach(
                formMetadataValue -> {
                  formNameToKey.put(
                      formMetadataValue.getResourceName(), formMetadataValue.getFormKey());
                });
      }
    }

    System.out.println("+++++++++++++");
    System.out.println(formNameToKey);

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withElementId("A")
                .limit(processInstanceCount))
        .describedAs("Expect that all jobs are created.")
        .hasSize(processInstanceCount);

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withElementId("A")
                .limit(processInstanceCount))
        .extracting(jobRecord -> jobRecord.getValue().getCustomHeaders())
        .isNotNull()
        .allMatch(
            headers ->
                headers.containsValue(
                    formNameToKey.get("/form/form_linkin_test_form_1.form").toString()));
  }
}
