/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformation;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.AdHocSubProcessTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.BoundaryEventTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.BusinessRuleTaskTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.CallActivityTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.CatchEventTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.ConditionalTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.ContextProcessTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.EndEventTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.ErrorTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.EscalationTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.EventBasedGatewayTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.ExclusiveGatewayTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.FlowElementInstantiationTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.FlowNodeTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.InclusiveGatewayTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.IntermediateCatchEventTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.IntermediateThrowEventTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.JobWorkerElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.MessageTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.MultiInstanceActivityTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.ProcessTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.ReceiveTaskTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.ScriptTaskTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.SequenceFlowTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.SignalTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.StartEventTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.SubProcessTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.UserTaskTransformer;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.SendTask;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.traversal.ModelWalker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Transforms a BPMN model into executable processes and, mirroring {@code EventAppliers}, acts as
 * the single registry of versioned sub-transformer handlers. Version 1 of every handler is
 * registered in the constructor against its {@link TransformerSlot}; versions greater than 1 are
 * registered via {@link #registerHandlerVersion} (at the end of the constructor once production
 * versions exist).
 *
 * <p>The same instance that transforms at deploy time also stamps {@link #currentVersionsById()}
 * into the {@code ProcessRecord}, and re-assembles the exact pinned pipeline at replay time via
 * {@link #transformDefinitions(BpmnModelInstance, Map)} — so deploy, stamping, and replay can never
 * disagree about which handler versions exist.
 */
public final class BpmnTransformer {

  /*
   * Step 1: Instantiate all elements in the process
   * Step 2: Transform all attributes, cross-link elements, etc.
   * Step 3: Modify elements based on the context
   * Step 4: Modify elements based on containing elements
   * Step 5: Modify elements based on containing container elements
   */
  private static final int STEP_COUNT = 5;

  private final ExpressionLanguage expressionLanguage;
  private final int maxNameFieldLength;

  /** (step, slot) pairs in registration order; determines which visitor runs which slot. */
  private final List<StepRegistration> stepRegistrations = new ArrayList<>();

  /** Per slot: all registered handler versions. v1 is put by {@code register(...)}. */
  private final Map<TransformerSlot, NavigableMap<Integer, Supplier<ModelElementTransformer<?>>>>
      handlerFactories = new EnumMap<>(TransformerSlot.class);

  public BpmnTransformer(final ExpressionLanguage expressionLanguage) {
    this(expressionLanguage, EngineConfiguration.DEFAULT_MAX_NAME_FIELD_LENGTH);
  }

  public BpmnTransformer(
      final ExpressionLanguage expressionLanguage, final int maxNameFieldLength) {
    this.expressionLanguage = expressionLanguage;
    this.maxNameFieldLength = maxNameFieldLength;

    // Step 1: Instantiate all elements in the process
    register(1, TransformerSlot.ERROR, ErrorTransformer::new);
    register(1, TransformerSlot.ESCALATION, EscalationTransformer::new);
    register(
        1, TransformerSlot.FLOW_ELEMENT_INSTANTIATION, FlowElementInstantiationTransformer::new);
    register(1, TransformerSlot.MESSAGE, MessageTransformer::new);
    register(1, TransformerSlot.SIGNAL, SignalTransformer::new);
    register(1, TransformerSlot.CONDITIONAL, ConditionalTransformer::new);
    register(1, TransformerSlot.PROCESS, ProcessTransformer::new);

    // Step 2: Transform all attributes, cross-link elements, etc.
    register(2, TransformerSlot.BOUNDARY_EVENT, BoundaryEventTransformer::new);
    register(2, TransformerSlot.BUSINESS_RULE_TASK, BusinessRuleTaskTransformer::new);
    register(2, TransformerSlot.CALL_ACTIVITY, CallActivityTransformer::new);
    register(2, TransformerSlot.CATCH_EVENT, CatchEventTransformer::new);
    register(2, TransformerSlot.CONTEXT_PROCESS, ContextProcessTransformer::new);
    register(2, TransformerSlot.END_EVENT, EndEventTransformer::new);
    register(2, TransformerSlot.FLOW_NODE, FlowNodeTransformer::new);
    register(
        2,
        TransformerSlot.SERVICE_TASK_JOB_WORKER,
        () -> new JobWorkerElementTransformer<>(ServiceTask.class));
    register(
        2,
        TransformerSlot.SEND_TASK_JOB_WORKER,
        () -> new JobWorkerElementTransformer<>(SendTask.class));
    register(2, TransformerSlot.RECEIVE_TASK, ReceiveTaskTransformer::new);
    register(2, TransformerSlot.SCRIPT_TASK, ScriptTaskTransformer::new);
    register(2, TransformerSlot.SEQUENCE_FLOW, SequenceFlowTransformer::new);
    register(2, TransformerSlot.START_EVENT, StartEventTransformer::new);
    register(2, TransformerSlot.USER_TASK, () -> new UserTaskTransformer(expressionLanguage));

    // Step 3: Modify elements based on the context
    register(3, TransformerSlot.CONTEXT_PROCESS, ContextProcessTransformer::new);
    register(3, TransformerSlot.EVENT_BASED_GATEWAY, EventBasedGatewayTransformer::new);
    register(3, TransformerSlot.EXCLUSIVE_GATEWAY, ExclusiveGatewayTransformer::new);
    register(3, TransformerSlot.INCLUSIVE_GATEWAY, InclusiveGatewayTransformer::new);
    register(3, TransformerSlot.INTERMEDIATE_CATCH_EVENT, IntermediateCatchEventTransformer::new);
    register(3, TransformerSlot.SUB_PROCESS, SubProcessTransformer::new);

    // Step 4: Modify elements based on containing elements
    register(4, TransformerSlot.CONTEXT_PROCESS, ContextProcessTransformer::new);
    register(4, TransformerSlot.INTERMEDIATE_THROW_EVENT, IntermediateThrowEventTransformer::new);
    register(4, TransformerSlot.AD_HOC_SUB_PROCESS, AdHocSubProcessTransformer::new);

    // Step 5: Modify elements based on containing container elements
    register(5, TransformerSlot.CONTEXT_PROCESS, ContextProcessTransformer::new);
    register(5, TransformerSlot.MULTI_INSTANCE_ACTIVITY, MultiInstanceActivityTransformer::new);

    // Versions > 1 are registered here as sub-transformers evolve, e.g.:
    // registerHandlerVersion(TransformerSlot.SIGNAL, 2, SignalTransformerV2::new);
  }

  /**
   * Registers a handler version greater than 1 for a slot. Version 1 lives in the constructor as
   * the original (frozen) class; to version a handler, write {@code XTransformerV2} (leaving the
   * original untouched) and register it here at version 2.
   */
  public void registerHandlerVersion(
      final TransformerSlot slot,
      final int version,
      final Supplier<ModelElementTransformer<?>> factory) {
    if (version <= TransformerSlot.DEFAULT_VERSION) {
      throw new IllegalArgumentException(
          "Version 1 handlers are registered in the constructor; got version "
              + version
              + " for "
              + slot);
    }
    handlerFactories.computeIfAbsent(slot, s -> new TreeMap<>()).put(version, factory);
  }

  /**
   * Current (highest registered) version for every slot above the default (1), keyed by {@link
   * TransformerSlot#id()}. Slots at the default version are omitted (sparse representation). This
   * map is written into {@code ProcessRecord} at deploy time so that replay can reconstruct the
   * exact same transformer pipeline.
   */
  public Map<Integer, Integer> currentVersionsById() {
    final Map<Integer, Integer> result = new HashMap<>();
    handlerFactories.forEach(
        (slot, versions) -> {
          final int highest = versions.lastKey();
          if (highest > TransformerSlot.DEFAULT_VERSION) {
            result.put(slot.id(), highest);
          }
        });
    return result;
  }

  /**
   * Transforms with the current (latest registered) version of every handler — the deploy-time
   * pipeline, matching what {@link #currentVersionsById()} stamps into the {@code ProcessRecord}.
   */
  public List<ExecutableProcess> transformDefinitions(final BpmnModelInstance modelInstance) {
    return transformDefinitions(modelInstance, currentVersionsById());
  }

  /**
   * Transforms with the handler versions pinned in {@code slotVersionsById} ({@link
   * TransformerSlot#id()} → version, as stored in {@code PersistedProcess}); absent slots use
   * version 1. The map is validated upfront: every pinned version above the default must have a
   * registered handler — even for elements not present in the model — otherwise an {@link
   * IllegalStateException} naming the slot and version is thrown before any transformation runs.
   */
  public List<ExecutableProcess> transformDefinitions(
      final BpmnModelInstance modelInstance, final Map<Integer, Integer> slotVersionsById) {
    final Map<TransformerSlot, Integer> slotVersions = validateVersions(slotVersionsById);

    final TransformContext context = new TransformContext();
    context.setExpressionLanguage(expressionLanguage);
    context.setMaxNameFieldLength(maxNameFieldLength);

    final ModelWalker walker = new ModelWalker(modelInstance);
    for (final TransformationVisitor visitor : buildVisitors(slotVersions)) {
      visitor.setContext(context);
      walker.walk(visitor);
    }

    return context.getProcesses();
  }

  private Map<TransformerSlot, Integer> validateVersions(
      final Map<Integer, Integer> slotVersionsById) {
    final Map<TransformerSlot, Integer> resolved = new EnumMap<>(TransformerSlot.class);
    slotVersionsById.forEach(
        (slotId, version) -> {
          final TransformerSlot slot = TransformerSlot.fromId(slotId);
          if (version > TransformerSlot.DEFAULT_VERSION && factoryFor(slot, version) == null) {
            throw new IllegalStateException(
                "Slot "
                    + slot
                    + " was pinned to version "
                    + version
                    + " at deploy time but no handler is registered at exactly that version."
                    + " BpmnTransformer is missing a handler that existed when the process was"
                    + " deployed.");
          }
          resolved.put(slot, version);
        });
    return resolved;
  }

  private TransformationVisitor[] buildVisitors(final Map<TransformerSlot, Integer> slotVersions) {
    final TransformationVisitor[] visitors = new TransformationVisitor[STEP_COUNT];
    Arrays.setAll(visitors, step -> new TransformationVisitor());
    for (final StepRegistration registration : stepRegistrations) {
      final int version =
          slotVersions.getOrDefault(registration.slot(), TransformerSlot.DEFAULT_VERSION);
      visitors[registration.step() - 1].registerHandler(
          factoryFor(registration.slot(), version).get());
    }
    return visitors;
  }

  private Supplier<ModelElementTransformer<?>> factoryFor(
      final TransformerSlot slot, final int version) {
    final var versions = handlerFactories.get(slot);
    return versions == null ? null : versions.get(version);
  }

  private void register(
      final int step,
      final TransformerSlot slot,
      final Supplier<ModelElementTransformer<?>> v1Factory) {
    stepRegistrations.add(new StepRegistration(step, slot));
    // putIfAbsent: CONTEXT_PROCESS is one logical transformer registered in steps 2-5
    handlerFactories
        .computeIfAbsent(slot, s -> new TreeMap<>())
        .putIfAbsent(TransformerSlot.DEFAULT_VERSION, v1Factory);
  }

  private record StepRegistration(int step, TransformerSlot slot) {}
}
