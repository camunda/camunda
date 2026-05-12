/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.value.BpmnElementType.SERVICE_TASK;
import static io.camunda.zeebe.protocol.record.value.BpmnElementType.SUB_PROCESS;
import static io.camunda.zeebe.protocol.record.value.BpmnElementType.USER_TASK;

import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Pure factory for Zeebe record bulk-operations for a single process instance.
 *
 * <p>All public methods return immutable lists of {@link BulkOperation}s — they never mutate
 * external state. Callers decide where to route the returned records.
 *
 * <p>Collaborates with {@link ZeebeRecordFactory} (DTO construction) and {@link
 * NodeTimingSimulator} (duration distribution) but owns no randomness beyond assigning user-task
 * assignees.
 */
class FlowNodeEmitter {

  // ── Key-space partitioning ────────────────────────────────────────────────
  // Ensures element-instance, user-task, incident, and agent-instance keys
  // from the same instanceKey never collide.

  static final long NODE_KEY_MULTIPLIER = 1_000L;
  static final long USER_TASK_KEY_MULTIPLIER = 2_000L;
  static final long INCIDENT_KEY_MULTIPLIER = 3_000L;
  static final long AGENT_INSTANCE_KEY_MULTIPLIER = 4_000L;

  private final ZeebeRecordFactory factory;
  private final NodeTimingSimulator timingSimulator;
  private final Random rng;

  FlowNodeEmitter(
      final ZeebeRecordFactory factory,
      final NodeTimingSimulator timingSimulator,
      final Random rng) {
    this.factory = factory;
    this.timingSimulator = timingSimulator;
    this.rng = rng;
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Returns all flow-node lifecycle records (ACTIVATING + optional COMPLETED/TERMINATED) for every
   * node in {@code layout}. Sub-process nodes are expanded in-place, children scoped correctly.
   *
   * @return a pair of immutable lists: {@code pi} for process-instance records, {@code ut} for
   *     user-task records
   */
  FlowNodeOps flowNodeOps(
      final InstanceContext ctx, final List<FlowNode> layout, final InstanceWindow window) {

    final long startMs = window.startMs();
    final long endMs = window.endMs();
    final long[] timings =
        window.isActive() ? null : timingSimulator.compute(layout, startMs, endMs);
    final FlowWalkContext flowWalkCtx = new FlowWalkContext(ctx, layout, timings, window);
    final WalkState walkState = WalkState.create();

    for (int ni = 0; ni < layout.size(); ni++) {
      if (walkState.consumed().contains(ni)) {
        continue;
      }
      final FlowNode node = layout.get(ni);
      if (node.type() == SUB_PROCESS) {
        collectSubProcess(flowWalkCtx, ni, walkState);
      } else {
        final long nodeStartMs = timings != null ? timings[ni] : window.startMs();
        final long nodeEndMs = timings != null ? timings[ni + 1] : window.endMs();
        final long instanceKey = ctx.instanceKey();
        final NodeEntry nodeEntry = new NodeEntry(node, instanceKey, nodeStartMs, nodeEndMs);
        collectNode(flowWalkCtx, nodeEntry, walkState);
      }
    }

    final List<BulkOperation> piOps = List.copyOf(walkState.pi());
    final List<BulkOperation> utOps = List.copyOf(walkState.ut());
    return new FlowNodeOps(piOps, utOps);
  }

  /**
   * Returns all variable records for {@code ctx}. Delegates value generation to {@link
   * ZeebeRecordFactory}/{@link VariableCatalogue}.
   */
  List<BulkOperation> variableOps(final InstanceContext ctx, final long timestamp) {
    return factory.variableOps(ctx, timestamp);
  }

  /**
   * Returns {@code UPDATED} variable records for {@code ctx}, simulating mid-flight variable
   * updates that cause the Optimize importer to merge into existing reporting-metrics docs.
   */
  List<BulkOperation> variableUpdateOps(final InstanceContext ctx, final long timestamp) {
    return factory.variableUpdateOps(ctx, timestamp);
  }

  /**
   * Returns a CREATED incident and optionally a RESOLVED incident anchored to the first service
   * task in {@code layout}. Returns an empty list when the layout has no service tasks.
   */
  List<BulkOperation> incidentOps(
      final InstanceContext ctx, final List<FlowNode> layout, final InstanceWindow window) {
    return layout.stream()
        .filter(n -> n.type() == SERVICE_TASK)
        .findFirst()
        .map(serviceTask -> buildIncidentOps(ctx, serviceTask, window))
        .orElse(List.of());
  }

  // ── Agent instance ────────────────────────────────────────────────────────

  // Predefined LLM model/provider pairs
  private static final String[][] LLM_CHOICES = {
    {"gpt-4o", "openai"},
    {"claude-sonnet-4-20250514", "anthropic"},
    {"gemini-2.0-flash", "google"},
    {"gpt-4o-mini", "openai"},
  };

  // AD_HOC tools are modelled in BPMN (elementId present); they become available after the agent
  // reads the BPMN schema during INITIALIZING — i.e. before the first THINKING update.
  private static final List<AgentTool> ADHOC_TOOLS =
      List.of(
          new AgentTool(
              "extract_data", "Extract structured data from a document", "extract-data-task"),
          new AgentTool(
              "validate_fields", "Validate fields against business rules", "validate-fields-task"),
          new AgentTool("lookup_record", "Look up a record by key", "lookup-record-task"),
          new AgentTool(
              "submit_decision", "Submit decision to downstream system", "submit-decision-task"));

  // MCP/A2A tools have no BPMN element; they are discovered during the TOOL_DISCOVERY phase and
  // therefore only appear in events after a TOOL_DISCOVERY UPDATED event.
  private static final List<AgentTool> MCP_TOOLS =
      List.of(
          new AgentTool("MCP_ocr___scan_document", "Scan document with OCR"),
          new AgentTool("MCP_crm___get_customer", "Retrieve customer data from CRM"),
          new AgentTool("MCP_slack___post_message", "Post update to a Slack channel"),
          new AgentTool("MCP_s3___read_file", "Read file content from S3"));

  // Synthetic element ID used for the AI agent task node (not in real BPMN files)
  private static final String AGENT_ELEMENT_ID = "ai-agent-task";

  /**
   * Returns the full agent instance lifecycle records (CREATED → UPDATED… → COMPLETED) for one
   * process instance, simulating a realistic agentic execution.
   *
   * <p>Lifecycle variants:
   *
   * <ul>
   *   <li><b>Active instance</b>: CREATED + 1 UPDATED (agent still running)
   *   <li><b>Terminated instance</b>: CREATED + 1 UPDATED + COMPLETED (cut short)
   *   <li><b>Completed instance</b>: CREATED + 2–4 UPDATED + COMPLETED (full run)
   * </ul>
   *
   * <p>Metrics in UPDATED/COMPLETED records carry engine-aggregated running totals, matching the
   * spec's dual-semantics contract.
   */
  List<BulkOperation> agentInstanceOps(
      final InstanceContext ctx, final InstanceWindow window, final long agentInstanceKey) {

    final long elemInstKey = ctx.instanceKey() * NODE_KEY_MULTIPLIER + 99L;
    final long duration = window.endMs() - window.startMs();
    final long startMs = window.startMs();

    // Randomly select LLM model and provider
    final String[] llm = LLM_CHOICES[rng.nextInt(LLM_CHOICES.length)];
    final String model = llm[0];
    final String provider = llm[1];
    final String systemPrompt = "Process this task efficiently using available tools.";

    // Limits (randomised)
    final long maxTokens = 4_000L + rng.nextInt(12_000);
    final int maxModelCalls = 5 + rng.nextInt(15);
    final int maxToolCalls = 10 + rng.nextInt(20);

    // AD_HOC tools (from BPMN schema) are always available from the first THINKING update.
    // MCP/A2A tools require a TOOL_DISCOVERY phase (~40 % of instances).
    final List<AgentTool> adhocTools = selectFromPool(ADHOC_TOOLS, 1 + rng.nextInt(3));
    final boolean doToolDiscovery = rng.nextDouble() < 0.4;
    final List<AgentTool> mcpTools =
        doToolDiscovery ? selectFromPool(MCP_TOOLS, 1 + rng.nextInt(3)) : List.of();
    final List<AgentTool> fullTools;
    if (doToolDiscovery) {
      final List<AgentTool> combined = new ArrayList<>(adhocTools);
      combined.addAll(mcpTools);
      fullTools = List.copyOf(combined);
    } else {
      fullTools = adhocTools;
    }

    final List<BulkOperation> ops = new ArrayList<>();

    // Event 1: CREATED (INITIALIZING, zero metrics, no tools yet)
    ops.add(
        factory.agentInstanceOp(
            ctx,
            new AgentInstanceEvent(
                agentInstanceKey,
                elemInstKey,
                AGENT_ELEMENT_ID,
                "CREATED",
                AgentInstanceStatus.INITIALIZING,
                model,
                provider,
                systemPrompt,
                maxTokens,
                maxModelCalls,
                maxToolCalls,
                0L,
                0L,
                0,
                0,
                List.of(),
                startMs)));

    if (window.isActive()) {
      // TOOL_DISCOVERY phase before the first THINKING (40 % of instances)
      if (doToolDiscovery) {
        ops.add(
            factory.agentInstanceOp(
                ctx,
                new AgentInstanceEvent(
                    agentInstanceKey,
                    elemInstKey,
                    AGENT_ELEMENT_ID,
                    "UPDATED",
                    AgentInstanceStatus.TOOL_DISCOVERY,
                    model,
                    provider,
                    systemPrompt,
                    maxTokens,
                    maxModelCalls,
                    maxToolCalls,
                    0L,
                    0L,
                    0,
                    0,
                    fullTools,
                    startMs + duration / 4)));
      }
      // Agent is still running — emit a partial THINKING update
      final long thinkingMs = startMs + duration / 2;
      final long inputTokens = 200L + rng.nextInt(600);
      final long outputTokens = 50L + rng.nextInt(250);
      ops.add(
          factory.agentInstanceOp(
              ctx,
              new AgentInstanceEvent(
                  agentInstanceKey,
                  elemInstKey,
                  AGENT_ELEMENT_ID,
                  "UPDATED",
                  AgentInstanceStatus.THINKING,
                  model,
                  provider,
                  systemPrompt,
                  maxTokens,
                  maxModelCalls,
                  maxToolCalls,
                  inputTokens,
                  outputTokens,
                  1,
                  0,
                  fullTools,
                  thinkingMs)));
      return List.copyOf(ops);
    }

    // Accumulated metric totals (updated after each phase)
    long totalInputTokens = 0L;
    long totalOutputTokens = 0L;
    int totalModelCalls = 0;
    int totalToolCalls = 0;

    if (window.isTerminated()) {
      // Short lifecycle: optional TOOL_DISCOVERY, one THINKING update, then COMPLETED
      if (doToolDiscovery) {
        ops.add(
            factory.agentInstanceOp(
                ctx,
                new AgentInstanceEvent(
                    agentInstanceKey,
                    elemInstKey,
                    AGENT_ELEMENT_ID,
                    "UPDATED",
                    AgentInstanceStatus.TOOL_DISCOVERY,
                    model,
                    provider,
                    systemPrompt,
                    maxTokens,
                    maxModelCalls,
                    maxToolCalls,
                    0L,
                    0L,
                    0,
                    0,
                    fullTools,
                    startMs + duration / 6)));
      }
      final long thinkingMs = startMs + duration / 3;
      totalInputTokens += 200L + rng.nextInt(600);
      totalOutputTokens += 50L + rng.nextInt(200);
      totalModelCalls++;
      ops.add(
          factory.agentInstanceOp(
              ctx,
              new AgentInstanceEvent(
                  agentInstanceKey,
                  elemInstKey,
                  AGENT_ELEMENT_ID,
                  "UPDATED",
                  AgentInstanceStatus.THINKING,
                  model,
                  provider,
                  systemPrompt,
                  maxTokens,
                  maxModelCalls,
                  maxToolCalls,
                  totalInputTokens,
                  totalOutputTokens,
                  totalModelCalls,
                  totalToolCalls,
                  fullTools,
                  thinkingMs)));
    } else {
      // Full lifecycle: optional TOOL_DISCOVERY → THINKING → TOOL_CALLING → (THINKING) → IDLE
      final long phase = duration / 6;

      // Optional TOOL_DISCOVERY before first THINKING
      if (doToolDiscovery) {
        ops.add(
            factory.agentInstanceOp(
                ctx,
                new AgentInstanceEvent(
                    agentInstanceKey,
                    elemInstKey,
                    AGENT_ELEMENT_ID,
                    "UPDATED",
                    AgentInstanceStatus.TOOL_DISCOVERY,
                    model,
                    provider,
                    systemPrompt,
                    maxTokens,
                    maxModelCalls,
                    maxToolCalls,
                    0L,
                    0L,
                    0,
                    0,
                    fullTools,
                    startMs + phase / 2)));
      }

      // Phase 1: THINKING (first model call, tools now available)
      final long thinking1Ms = startMs + phase;
      totalInputTokens += 200L + rng.nextInt(800);
      totalOutputTokens += 50L + rng.nextInt(300);
      totalModelCalls++;
      ops.add(
          factory.agentInstanceOp(
              ctx,
              new AgentInstanceEvent(
                  agentInstanceKey,
                  elemInstKey,
                  AGENT_ELEMENT_ID,
                  "UPDATED",
                  AgentInstanceStatus.THINKING,
                  model,
                  provider,
                  systemPrompt,
                  maxTokens,
                  maxModelCalls,
                  maxToolCalls,
                  totalInputTokens,
                  totalOutputTokens,
                  totalModelCalls,
                  totalToolCalls,
                  fullTools,
                  thinking1Ms)));

      // Phase 2: TOOL_CALLING
      final long toolCallingMs = startMs + phase * 2;
      final int toolCallDelta = 1 + rng.nextInt(3);
      totalToolCalls += toolCallDelta;
      ops.add(
          factory.agentInstanceOp(
              ctx,
              new AgentInstanceEvent(
                  agentInstanceKey,
                  elemInstKey,
                  AGENT_ELEMENT_ID,
                  "UPDATED",
                  AgentInstanceStatus.TOOL_CALLING,
                  model,
                  provider,
                  systemPrompt,
                  maxTokens,
                  maxModelCalls,
                  maxToolCalls,
                  totalInputTokens,
                  totalOutputTokens,
                  totalModelCalls,
                  totalToolCalls,
                  fullTools,
                  toolCallingMs)));

      // Phase 3: second THINKING (60 % of instances do a second model call)
      if (rng.nextDouble() < 0.6) {
        final long thinking2Ms = startMs + phase * 3;
        totalInputTokens += 100L + rng.nextInt(500);
        totalOutputTokens += 30L + rng.nextInt(200);
        totalModelCalls++;
        ops.add(
            factory.agentInstanceOp(
                ctx,
                new AgentInstanceEvent(
                    agentInstanceKey,
                    elemInstKey,
                    AGENT_ELEMENT_ID,
                    "UPDATED",
                    AgentInstanceStatus.THINKING,
                    model,
                    provider,
                    systemPrompt,
                    maxTokens,
                    maxModelCalls,
                    maxToolCalls,
                    totalInputTokens,
                    totalOutputTokens,
                    totalModelCalls,
                    totalToolCalls,
                    fullTools,
                    thinking2Ms)));
      }

      // Phase 4: IDLE (agent finished its work, awaiting next activation)
      final long idleMs = startMs + phase * 5;
      ops.add(
          factory.agentInstanceOp(
              ctx,
              new AgentInstanceEvent(
                  agentInstanceKey,
                  elemInstKey,
                  AGENT_ELEMENT_ID,
                  "UPDATED",
                  AgentInstanceStatus.IDLE,
                  model,
                  provider,
                  systemPrompt,
                  maxTokens,
                  maxModelCalls,
                  maxToolCalls,
                  totalInputTokens,
                  totalOutputTokens,
                  totalModelCalls,
                  totalToolCalls,
                  fullTools,
                  idleMs)));
    }

    // Final event: COMPLETED (terminal, carries final running totals, triggered by process end)
    ops.add(
        factory.agentInstanceOp(
            ctx,
            new AgentInstanceEvent(
                agentInstanceKey,
                elemInstKey,
                AGENT_ELEMENT_ID,
                "COMPLETED",
                AgentInstanceStatus.COMPLETED,
                model,
                provider,
                systemPrompt,
                maxTokens,
                maxModelCalls,
                maxToolCalls,
                totalInputTokens,
                totalOutputTokens,
                totalModelCalls,
                totalToolCalls,
                fullTools,
                window.endMs())));

    return List.copyOf(ops);
  }

  /** Selects {@code count} distinct tools at random from the given {@code pool}. */
  private List<AgentTool> selectFromPool(final List<AgentTool> pool, final int count) {
    final List<AgentTool> shuffled = new ArrayList<>(pool);
    Collections.shuffle(shuffled, rng);
    return List.copyOf(shuffled.subList(0, Math.min(count, shuffled.size())));
  }

  // ── Private incident builder ──────────────────────────────────────────────

  /**
   * Builds CREATED and optionally RESOLVED incident records for one service task. Terminated
   * instances always resolve; completed ones resolve ~50 % of the time.
   */
  private List<BulkOperation> buildIncidentOps(
      final InstanceContext ctx, final FlowNode serviceTask, final InstanceWindow window) {

    final long elemInstKey = ctx.instanceKey() * NODE_KEY_MULTIPLIER + serviceTask.index();
    final long incKey = ctx.instanceKey() * INCIDENT_KEY_MULTIPLIER;
    final long incCreateMs = window.startMs() + (window.endMs() - window.startMs()) / 5;

    final IncidentEvent createdEvent =
        new IncidentEvent(incKey, elemInstKey, serviceTask.id(), incCreateMs);
    final BulkOperation createdOp = factory.incidentOp(ctx, createdEvent, IncidentIntent.CREATED);

    final boolean shouldResolve = window.isTerminated() || rng.nextBoolean();
    if (!shouldResolve) {
      return List.of(createdOp);
    }

    final IncidentEvent resolvedEvent =
        new IncidentEvent(incKey, elemInstKey, serviceTask.id(), incCreateMs + 60_000L);
    final BulkOperation resolvedOp =
        factory.incidentOp(ctx, resolvedEvent, IncidentIntent.RESOLVED);
    return List.of(createdOp, resolvedOp);
  }

  // ── Private collectors ────────────────────────────────────────────────────

  /**
   * Collects records for one embedded sub-process and all of its child nodes into {@code
   * walkState}.
   */
  private void collectSubProcess(
      final FlowWalkContext flowWalkCtx, final int subProcessNi, final WalkState walkState) {

    final List<FlowNode> layout = flowWalkCtx.layout();
    final long[] timings = flowWalkCtx.timings();
    final InstanceContext ctx = flowWalkCtx.ctx();
    final InstanceWindow window = flowWalkCtx.window();
    final FlowNode subProcess = layout.get(subProcessNi);

    final List<Integer> childNis =
        IntStream.range(subProcessNi + 1, layout.size())
            .boxed()
            .takeWhile(ci -> layout.get(ci).parentIndex() == subProcess.index())
            .toList();
    childNis.forEach(walkState.consumed()::add);

    final boolean hasTimingsAndChildren = timings != null && !childNis.isEmpty();
    final long subStartMs =
        hasTimingsAndChildren
            ? timings[childNis.get(0)]
            : (timings != null ? timings[subProcessNi] : window.startMs());
    final long subEndMs =
        hasTimingsAndChildren
            ? timings[childNis.get(childNis.size() - 1) + 1]
            : (timings != null ? timings[subProcessNi + 1] : window.endMs());

    final long instanceKey = ctx.instanceKey();
    final String subProcessId = subProcess.id();
    final BpmnElementType subType = subProcess.type();
    final long subElemInstKey = instanceKey * NODE_KEY_MULTIPLIER + subProcess.index();
    final ElementRecord subElement =
        new ElementRecord(subElemInstKey, subProcessId, subType, instanceKey);

    final LifecycleEvent subStartEvent = new LifecycleEvent(ELEMENT_ACTIVATING, subStartMs);
    final BulkOperation subStartOp = factory.processInstanceOp(ctx, subElement, subStartEvent);
    walkState.pi().add(subStartOp);

    childNis.stream()
        .map(
            ci -> {
              final FlowNode child = layout.get(ci);
              final long childStart = timings != null ? timings[ci] : window.startMs();
              final long childEnd = timings != null ? timings[ci + 1] : window.endMs();
              return new NodeEntry(child, subElemInstKey, childStart, childEnd);
            })
        .forEach(childEntry -> collectNode(flowWalkCtx, childEntry, walkState));

    if (!window.isActive()) {
      final LifecycleEvent subEndEvent = new LifecycleEvent(window.endIntent(), subEndMs);
      final BulkOperation subEndOp = factory.processInstanceOp(ctx, subElement, subEndEvent);
      walkState.pi().add(subEndOp);
    }
  }

  /**
   * Collects ACTIVATING (and optionally COMPLETED/TERMINATED) records for a single leaf node, plus
   * user-task lifecycle records when the node is a {@code USER_TASK}.
   */
  private void collectNode(
      final FlowWalkContext flowWalkCtx, final NodeEntry nodeEntry, final WalkState walkState) {

    final InstanceContext ctx = flowWalkCtx.ctx();
    final InstanceWindow window = flowWalkCtx.window();
    final FlowNode node = nodeEntry.node();

    final String elementId = node.id();
    final BpmnElementType elementType = node.type();
    final long flowScopeKey = nodeEntry.flowScopeKey();
    final long elemInstKey = ctx.instanceKey() * NODE_KEY_MULTIPLIER + node.index();
    final ElementRecord element =
        new ElementRecord(elemInstKey, elementId, elementType, flowScopeKey);

    final LifecycleEvent startEvent = new LifecycleEvent(ELEMENT_ACTIVATING, nodeEntry.startMs());
    final BulkOperation startOp = factory.processInstanceOp(ctx, element, startEvent);
    walkState.pi().add(startOp);

    if (!window.isActive()) {
      final LifecycleEvent endEvent = new LifecycleEvent(window.endIntent(), nodeEntry.endMs());
      final BulkOperation endOp = factory.processInstanceOp(ctx, element, endEvent);
      walkState.pi().add(endOp);

      if (node.type() == USER_TASK) {
        final boolean isTerminated = window.isTerminated();
        final long nodeStartMs = nodeEntry.startMs();
        final long nodeEndMs = nodeEntry.endMs();
        final UserTaskEntry userTaskEntry =
            new UserTaskEntry(node, elemInstKey, isTerminated, nodeStartMs, nodeEndMs);
        collectUserTask(ctx, userTaskEntry, walkState);
      }
    }
  }

  /** Builds CREATING, ASSIGNED, and COMPLETED/CANCELED records for one user-task node. */
  private void collectUserTask(
      final InstanceContext ctx, final UserTaskEntry userTaskEntry, final WalkState walkState) {

    final FlowNode node = userTaskEntry.node();
    final long elemInstKey = userTaskEntry.elemInstKey();
    final String nodeId = node.id();
    final long utKey = ctx.instanceKey() * USER_TASK_KEY_MULTIPLIER + node.index();
    final long assignedMs =
        userTaskEntry.startMs() + (userTaskEntry.endMs() - userTaskEntry.startMs()) / 4;
    final String assignee = "user-" + (1 + rng.nextInt(10));
    final UserTaskIntent endIntent =
        userTaskEntry.isTerminated() ? UserTaskIntent.CANCELED : UserTaskIntent.COMPLETED;

    final UserTaskEvent creatingEvent =
        new UserTaskEvent(utKey, elemInstKey, nodeId, userTaskEntry.startMs());
    final UserTaskEvent assignedEvent = new UserTaskEvent(utKey, elemInstKey, nodeId, assignedMs);
    final UserTaskEvent endEvent =
        new UserTaskEvent(utKey, elemInstKey, nodeId, userTaskEntry.endMs());

    final BulkOperation creatingOp = factory.userTaskCreatingOp(ctx, creatingEvent);
    final BulkOperation assignedOp = factory.userTaskAssignedOp(ctx, assignedEvent, assignee);
    final BulkOperation endOp = factory.userTaskEndOp(ctx, endEvent, endIntent);
    walkState.ut().addAll(List.of(creatingOp, assignedOp, endOp));
  }

  // ── Private parameter objects ─────────────────────────────────────────────

  /** Immutable context shared across all collect calls for one {@link #flowNodeOps} invocation. */
  private record FlowWalkContext(
      InstanceContext ctx, List<FlowNode> layout, long[] timings, InstanceWindow window) {}

  /** Groups a leaf flow-node with its timing and flow-scope key for one execution step. */
  private record NodeEntry(FlowNode node, long flowScopeKey, long startMs, long endMs) {}

  /** Groups a user-task node with its element-instance key, termination flag, and timing. */
  private record UserTaskEntry(
      FlowNode node, long elemInstKey, boolean isTerminated, long startMs, long endMs) {}

  /**
   * Mutable accumulator for all records collected during one {@link #flowNodeOps} walk.
   *
   * <p>The record holds references to mutable collections; the record itself is not replaced. After
   * the walk completes, {@link #flowNodeOps} seals the lists with {@link List#copyOf}.
   */
  private record WalkState(List<BulkOperation> pi, List<BulkOperation> ut, Set<Integer> consumed) {

    static WalkState create() {
      return new WalkState(new ArrayList<>(), new ArrayList<>(), new HashSet<>());
    }
  }

  // ── Return type ───────────────────────────────────────────────────────────

  /** Immutable pair of process-instance and user-task records produced by one flow-node walk. */
  record FlowNodeOps(List<BulkOperation> pi, List<BulkOperation> ut) {}
}
