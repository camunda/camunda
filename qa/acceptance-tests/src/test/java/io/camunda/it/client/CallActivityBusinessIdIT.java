/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.CallActivityBuilder;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage that a call activity's {@code businessId} is resolved when the child instance
 * is created and is then observable through the process instance search API across all backends.
 *
 * <p>Engine unit tests already assert the resolution semantics against the internal record stream;
 * these tests add the missing cross-component leg (engine → exporter → search index → client) and
 * confirm the child instance is queryable by its resolved Business ID.
 *
 * <p>Each test uses its own parent/child process ids and locates the child by {@code
 * parentProcessInstanceKey}, so tests never observe each other's instances.
 */
@MultiDbTest
public class CallActivityBusinessIdIT {

  private static CamundaClient client;

  @Test
  void shouldOverrideChildBusinessIdWithLiteral() {
    // given - a call activity that assigns a literal child Business ID
    final String parentProcessId =
        deployParentWithChild("literal", c -> c.zeebeBusinessId("child-literal"));

    // when - the parent starts without a Business ID of its own
    final var parent = startParent(parentProcessId, null, Map.of());

    // then - the child instance carries the literal, not the (absent) parent value
    assertThat(awaitChild(parent.getProcessInstanceKey()).getBusinessId())
        .isEqualTo("child-literal");
  }

  @Test
  void shouldInheritParentBusinessIdWhenAttributeIsAbsent() {
    // given - a call activity without a businessId attribute
    final String parentProcessId = deployParentWithChild("inherit", c -> {});

    // when - the parent carries a Business ID
    final var parent = startParent(parentProcessId, "order-inherit", Map.of());

    // then - the child inherits the parent's Business ID
    assertThat(awaitChild(parent.getProcessInstanceKey()).getBusinessId())
        .isEqualTo("order-inherit");
  }

  @Test
  void shouldResolveEmptyChildBusinessIdToNull() {
    // given - a call activity that explicitly clears the child Business ID
    final String parentProcessId = deployParentWithChild("empty", c -> c.zeebeBusinessId(""));

    // when - the parent carries a Business ID
    final var parent = startParent(parentProcessId, "order-empty", Map.of());

    // then - the child does not inherit; its Business ID is null
    assertThat(awaitChild(parent.getProcessInstanceKey()).getBusinessId()).isNull();
  }

  @Test
  void shouldResolveFeelChildBusinessIdFromVariable() {
    // given - a call activity whose businessId is a FEEL expression over a process variable
    final String parentProcessId =
        deployParentWithChild("feel-var", c -> c.zeebeBusinessId("=orderCode"));

    // when - the variable is provided at parent creation
    final Map<String, Object> variables = Map.of("orderCode", "feel-child-42");
    final var parent = startParent(parentProcessId, "order-feel", variables);

    // then - the child Business ID is the resolved expression value
    assertThat(awaitChild(parent.getProcessInstanceKey()).getBusinessId())
        .isEqualTo("feel-child-42");
  }

  @Test
  void shouldAppendSuffixToParentBusinessIdViaExpressionContext() {
    // given - a call activity that derives the child Business ID from the parent's, using the
    // camunda.processInstance.businessId context expression
    final String parentProcessId =
        deployParentWithChild(
            "ctx-suffix",
            c -> c.zeebeBusinessId("=camunda.processInstance.businessId + \"-child\""));

    // when - the parent carries a Business ID
    final var parent = startParent(parentProcessId, "order-parent", Map.of());

    // then - the child Business ID is the parent's with the suffix appended
    assertThat(awaitChild(parent.getProcessInstanceKey()).getBusinessId())
        .isEqualTo("order-parent-child");
  }

  private static String deployParentWithChild(
      final String suffix, final Consumer<CallActivityBuilder> callActivityCustomizer) {
    final String childProcessId = "child-" + suffix;
    final String parentProcessId = "parent-" + suffix;

    final BpmnModelInstance child =
        Bpmn.createExecutableProcess(childProcessId).startEvent().endEvent().done();
    final BpmnModelInstance parent =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(
                "call",
                c -> {
                  c.zeebeProcessId(childProcessId);
                  callActivityCustomizer.accept(c);
                })
            .endEvent()
            .done();

    deployProcessAndWaitForIt(client, child, childProcessId + ".bpmn");
    deployProcessAndWaitForIt(client, parent, parentProcessId + ".bpmn");
    return parentProcessId;
  }

  private static ProcessInstanceEvent startParent(
      final String parentProcessId, final String businessId, final Map<String, Object> variables) {
    var command = client.newCreateInstanceCommand().bpmnProcessId(parentProcessId).latestVersion();
    if (businessId != null) {
      command = command.businessId(businessId);
    }
    if (!variables.isEmpty()) {
      command = command.variables(variables);
    }
    return command.execute();
  }

  private static ProcessInstance awaitChild(final long parentProcessInstanceKey) {
    final AtomicReference<ProcessInstance> child = new AtomicReference<>();
    Awaitility.await("child process instance is exported to the search index")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var items =
                  client
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.parentProcessInstanceKey(parentProcessInstanceKey))
                      .send()
                      .join()
                      .items();
              assertThat(items).hasSize(1);
              child.set(items.getFirst());
            });
    return child.get();
  }
}
