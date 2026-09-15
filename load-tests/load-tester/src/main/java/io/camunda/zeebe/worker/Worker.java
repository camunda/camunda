/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.worker;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryItem;
import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.worker.JobClient;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.WorkerProperties;
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.camunda.zeebe.util.PayloadReader;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class Worker {

  private static final Logger LOGGER = LoggerFactory.getLogger(Worker.class);
  private static final Logger THROTTLED_LOGGER = new ThrottledLogger(LOGGER, Duration.ofSeconds(5));
  private static final int REQUEST_FUTURES_CAPACITY = 10_000;

  // Job type of the agent-visibility scenario's ad-hoc sub-process ("AI Agent" orchestrator
  // element in agentTools.bpmn). Jobs of this type are completed via the ad-hoc-sub-process
  // JobResult flavor (round-schedule + tool activation) instead of the plain completion path
  // below; every other job type (the scenario's 3 tool roles, and every other scenario's
  // worker role) is unaffected.
  private static final String AD_HOC_SUB_PROCESS_JOB_TYPE = "agent-visibility-orchestrator";

  // Fixed, deliberately non-configurable tool-calling schedule for the agent-visibility
  // scenario: round 1 activates one tool, round 2 activates two tools in the same job result
  // (parallel tool calls), round 3 activates one tool again, round 4 activates none and instead
  // fulfills the completion condition. Baseline and treatment runs of the scenario must produce
  // byte-identical tool-activation traffic, so this schedule is never driven by Helm/env config.
  private static final List<List<String>> AD_HOC_SUB_PROCESS_ROUND_SCHEDULE =
      List.of(
          List.of("tool-lookup-account"),
          List.of("tool-calculate-score", "tool-send-notification"),
          List.of("tool-lookup-account"),
          List.of());

  private final CamundaClient client;
  private final WorkerProperties workerCfg;
  private final String variables;
  private final BlockingQueue<Future<?>> requestFutures =
      new ArrayBlockingQueue<>(REQUEST_FUTURES_CAPACITY);
  private final ResponseChecker responseChecker;
  private final ConnectionMonitor connectionMonitor;

  // Per-process-instance round tracking for the ad-hoc-sub-process orchestration path, keyed by
  // ActivatedJob#getProcessInstanceKey(). Each RoundTracker assigns a distinct round number to
  // every distinct ActivatedJob#getKey() it sees, so a redelivered job - which Zeebe job workers
  // permit even with stream-enabled=false, since job workers are at-least-once, not
  // exactly-once - reuses its already-assigned round instead of advancing past it. A bare
  // incrementing counter (the previous implementation) double-counts a redelivered job as a new
  // round, desyncing the round number from the process instance's actual physical progress and
  // corrupting adHocSubProcessAgentInstanceKeys below; see
  // ctxt/results/2026-09-09-run2-treatment-then-baseline.md for the discovered failure mode.
  // In-memory and per-worker-pod: exact with the scenario's default single orchestrator replica;
  // approximate (rounds could interleave across pods) if that role is ever scaled beyond one
  // replica. Entries are evicted once the final round completes, to keep this bounded over
  // long-running soak tests.
  private final ConcurrentHashMap<Long, RoundTracker> adHocSubProcessRounds =
      new ConcurrentHashMap<>();

  // Caches the AgentInstance key returned by the round-1 CREATE, keyed by process instance key,
  // so later rounds' UPDATE calls (Worker#simulateAgentInstance) can address the same agent
  // instance. Only populated/consulted when WorkerProperties#agentInstanceSimulationEnabled is
  // true; evicted alongside the round counter once the final round completes.
  private final ConcurrentHashMap<Long, Long> adHocSubProcessAgentInstanceKeys =
      new ConcurrentHashMap<>();

  public Worker(
      final CamundaClient client,
      final LoadTesterProperties properties,
      final PayloadReader payloadReader,
      final ConnectionMonitor connectionMonitor) {
    this.client = client;
    workerCfg = properties.getWorker();
    variables = payloadReader.readPayload(workerCfg.getPayloadPath());
    responseChecker = new ResponseChecker(requestFutures);
    this.connectionMonitor = connectionMonitor;
  }

  @PostConstruct
  void awaitTopologyAndLogConfig() {
    responseChecker.start();
    connectionMonitor.awaitAndPrintTopology();
    LOGGER.info(
        "Worker config: completionDelay={}, sendMessage={}, messageName={}, "
            + "correlationKeyVariable={}, payloadPath={}",
        workerCfg.getCompletionDelay(),
        workerCfg.isSendMessage(),
        workerCfg.getMessageName(),
        workerCfg.getCorrelationKeyVariableName(),
        workerCfg.getPayloadPath());
  }

  @PreDestroy
  void shutdown() {
    // ResponseChecker extends Thread with a default (non-daemon) factory, so without
    // an explicit close() it keeps the JVM alive after the Spring context stops —
    // tests appear to pass but the forked process never exits on IDE runners.
    responseChecker.close();
    try {
      responseChecker.join(Duration.ofSeconds(5).toMillis());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @JobWorker(autoComplete = false)
  public void handleJob(final JobClient jobClient, final ActivatedJob job) {
    if (AD_HOC_SUB_PROCESS_JOB_TYPE.equals(job.getType())) {
      handleAdHocSubProcessOrchestration(jobClient, job);
      return;
    }

    final long startHandlingTime = System.currentTimeMillis();

    if (workerCfg.isSendMessage()) {
      final var correlationKey =
          job.getVariable(workerCfg.getCorrelationKeyVariableName()).toString();

      final boolean messagePublishedSuccessfully = publishMessage(correlationKey);
      if (!messagePublishedSuccessfully) {
        // Instead of failing the job, we simply let the job time out, so someone else has to
        // pick up the job later. This might delay the individual process instance, but overall it
        // has a lesser impact, as we can work on a different job in the meantime, keeping up the
        // throughput.
        //
        // It might be that one partition has currently some struggle due to restarts or role
        // changes, chances are low that this affects all partitions.
        //
        // This might cause issues for the current job to publish a message, but we are sending
        // messages via correlation key,   based on the process instance payload.
        //
        // On the next job/message published the chances are (partition count - 1 / partition
        // count) that we hit another partition where it works without issues.
        //
        // Apply the same completion delay as the success path before returning. Without it the
        // handler returns immediately, the client re-polls at full speed, and we keep hammering
        // the struggling partition, defeating the time-out-and-retry strategy above.
        addDelayToCompletion(workerCfg.getCompletionDelay().toMillis(), startHandlingTime);
        return;
      }
    }

    final var command = jobClient.newCompleteCommand(job.getKey()).variables(variables);
    addDelayToCompletion(workerCfg.getCompletionDelay().toMillis(), startHandlingTime);
    if (!requestFutures.offer(command.send())) {
      // Non-blocking: if the response-check queue is saturated, drop tracking for this
      // completion rather than stalling the job handler thread (which would cascade into
      // broker timeouts). We lose visibility into its eventual result — log throttled so
      // the operator can notice sustained backpressure without flooding the log.
      THROTTLED_LOGGER.warn(
          "Completion-response queue full (capacity: {}); dropping future tracking",
          REQUEST_FUTURES_CAPACITY);
    }
  }

  // Completes an agent-visibility scenario's ad-hoc-sub-process orchestrator job by following
  // AD_HOC_SUB_PROCESS_ROUND_SCHEDULE: activates this round's tool(s) via the ad-hoc-sub-process
  // JobResult flavor, or - on the final, empty round - fulfills the completion condition instead.
  // The engine automatically creates a fresh job of the same type on the same element instance
  // once the activated tool(s) complete, so no further loop-control is needed here; the next
  // round is simply the next invocation of this method for the same process instance.
  private void handleAdHocSubProcessOrchestration(
      final JobClient jobClient, final ActivatedJob job) {
    final long startHandlingTime = System.currentTimeMillis();
    final long processInstanceKey = job.getProcessInstanceKey();
    final int round =
        adHocSubProcessRounds
            .computeIfAbsent(processInstanceKey, key -> new RoundTracker())
            .roundFor(job.getKey());
    final boolean isFinalRound = round == AD_HOC_SUB_PROCESS_ROUND_SCHEDULE.size() - 1;
    final var toolsToActivate = AD_HOC_SUB_PROCESS_ROUND_SCHEDULE.get(round);

    if (workerCfg.isAgentInstanceSimulationEnabled()) {
      simulateAgentInstance(job, round, isFinalRound);
    }

    // Evicted only after this round's work (including the AgentInstance command above) has
    // fully run, not before - evicting earlier would let a redelivered copy of this same final
    // round see a fresh, empty RoundTracker and get miscategorized back to round 0.
    if (isFinalRound) {
      adHocSubProcessRounds.remove(processInstanceKey);
    }

    addDelayToCompletion(workerCfg.getCompletionDelay().toMillis(), startHandlingTime);

    final var command =
        jobClient
            .newCompleteCommand(job)
            .withResult(
                resultStep -> {
                  CompleteAdHocSubProcessResultStep1 adHocResult = resultStep.forAdHocSubProcess();
                  for (final var tool : toolsToActivate) {
                    adHocResult = adHocResult.activateElement(tool);
                  }
                  return adHocResult.completionConditionFulfilled(isFinalRound);
                });
    if (!requestFutures.offer(command.send())) {
      THROTTLED_LOGGER.warn(
          "Completion-response queue full (capacity: {}); dropping future tracking",
          REQUEST_FUTURES_CAPACITY);
    }
  }

  // Issues the AgentInstance CREATE (round 1) or UPDATE (later rounds) call a real Connector
  // would issue for this round, using synthetic history content - see docs/testing or the
  // agent-visibility scenario plan for why no real LLM/Connector is involved. Called before the
  // job is completed, so the extra command latency is genuinely part of what gets measured.
  private void simulateAgentInstance(
      final ActivatedJob job, final int round, final boolean isFinalRound) {
    final long processInstanceKey = job.getProcessInstanceKey();

    if (round == 0) {
      final var configurationItem =
          new AgentInstanceHistoryItem()
              .historyItemId("agent-visibility-configuration")
              .loopIteration(1)
              .role(AgentInstanceHistoryRole.CONFIGURATION)
              .content(
                  List.of(
                      AgentInstanceHistoryContent.text(
                          "Synthetic agent configuration for load testing.")))
              .producedAt(OffsetDateTime.now())
              .model("synthetic-load-test-model")
              .provider("synthetic")
              .systemPrompt(
                  List.of(
                      AgentInstanceHistoryContent.text("You are a synthetic load-test agent.")));

      final var response =
          client
              .newCreateAgentInstanceCommand()
              .elementInstanceKey(job.getElementInstanceKey())
              .jobKey(job.getKey())
              .jobLease(job.getLeaseToken())
              .history(List.of(configurationItem))
              .send()
              .join();
      adHocSubProcessAgentInstanceKeys.put(processInstanceKey, response.getAgentInstanceKey());
    } else {
      final Long agentInstanceKey = adHocSubProcessAgentInstanceKeys.get(processInstanceKey);
      if (agentInstanceKey == null) {
        // Defensive only - RoundTracker (see adHocSubProcessRounds) and the reordered eviction
        // above should prevent this for any redelivered job, but a cache miss here must never
        // throw: an uncaught exception leaves the job unable to complete, and (per the discovery
        // in ctxt/results/2026-09-09-run2-treatment-then-baseline.md) the framework's own FAIL
        // fallback can itself be rejected by a concurrent redelivery holding the lease,
        // permanently stranding the job. Skipping the UPDATE lets the round schedule below still
        // complete the job normally; only this one AgentHistory item is missed.
        THROTTLED_LOGGER.warn(
            "No cached AgentInstance key for processInstanceKey={} at round={}; skipping "
                + "AgentInstance UPDATE",
            processInstanceKey,
            round);
      } else {
        final var assistantItem =
            new AgentInstanceHistoryItem()
                .historyItemId("agent-visibility-round-" + (round + 1))
                .loopIteration(round + 1)
                .role(AgentInstanceHistoryRole.ASSISTANT)
                .content(
                    List.of(
                        AgentInstanceHistoryContent.text(
                            "Synthetic assistant message for round " + (round + 1) + ".")))
                .producedAt(OffsetDateTime.now());

        client
            .newUpdateAgentInstanceCommand(agentInstanceKey)
            .elementInstanceKey(job.getElementInstanceKey())
            .status(
                isFinalRound ? AgentInstanceUpdateStatus.IDLE : AgentInstanceUpdateStatus.THINKING)
            .jobKey(job.getKey())
            .jobLease(job.getLeaseToken())
            .history(List.of(assistantItem))
            .send()
            .join();
      }
    }

    if (isFinalRound) {
      adHocSubProcessAgentInstanceKeys.remove(processInstanceKey);
    }
  }

  private boolean publishMessage(final String correlationKey) {
    final var messageName = workerCfg.getMessageName();

    LOGGER.debug("Publish message '{}' with correlation key '{}'", messageName, correlationKey);
    final var messageSendFuture =
        client
            .newPublishMessageCommand()
            .messageName(messageName)
            .correlationKey(correlationKey)
            .send();

    try {
      messageSendFuture.get(10, TimeUnit.SECONDS);
      return true;
    } catch (final Exception ex) {
      THROTTLED_LOGGER.error(
          "Exception on publishing a message with name {} and correlationKey {}",
          messageName,
          correlationKey,
          ex);
      return false;
    }
  }

  private static void addDelayToCompletion(
      final long completionDelay, final long startHandlingTime) {
    try {
      final var elapsedTime = System.currentTimeMillis() - startHandlingTime;
      if (elapsedTime < completionDelay) {
        final long sleepTime = completionDelay - elapsedTime;
        LOGGER.debug("Sleep for {} ms", sleepTime);
        Thread.sleep(sleepTime);
      } else {
        LOGGER.debug(
            "Skip sleep. Elapsed time {} is larger than {} completion delay.",
            elapsedTime,
            completionDelay);
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      THROTTLED_LOGGER.error(
          "Interrupted during completion delay sleep of {} ms", completionDelay, e);
    } catch (final Exception e) {
      THROTTLED_LOGGER.error("Exception on sleep with completion delay {}", completionDelay, e);
    }
  }

  // Assigns each distinct ActivatedJob#getKey() exactly one round number for a given process
  // instance. Zeebe job workers are at-least-once, not exactly-once, so the same job can be
  // delivered more than once; computeIfAbsent guarantees the round-generating lambda runs at
  // most once per job key even under concurrent redelivery, so a redelivered job always reuses
  // its already-assigned round instead of advancing past it.
  private static final class RoundTracker {

    private final ConcurrentHashMap<Long, Integer> roundsByJobKey = new ConcurrentHashMap<>();
    private final AtomicInteger nextRound = new AtomicInteger(0);

    int roundFor(final long jobKey) {
      return roundsByJobKey.computeIfAbsent(jobKey, key -> nextRound.getAndIncrement());
    }
  }
}
