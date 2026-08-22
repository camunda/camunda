/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.runner.internal;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.runner.Cluster;
import io.camunda.runner.Run;
import io.camunda.runner.RunOptions;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Phase 2 runner pipeline: clone+rewrite -> deploy -> register workers -> create N instances. */
public final class RunnerPipeline {

  private static final Logger LOG = LoggerFactory.getLogger(RunnerPipeline.class);

  private RunnerPipeline() {}

  public static Run execute(
      final BpmnModelInstance model,
      final Map<BindingKey, BoundHandler> bindings,
      final int instanceCount,
      final Cluster cluster) {
    return execute(model, bindings, RunOptions.of(instanceCount), cluster);
  }

  public static Run execute(
      final BpmnModelInstance model,
      final Map<BindingKey, BoundHandler> bindings,
      final RunOptions opts,
      final Cluster cluster) {
    final int instanceCount = opts.instances();
    if (instanceCount < 0) {
      throw new IllegalArgumentException("instanceCount must be >= 0, got " + instanceCount);
    }

    // Defensive: user-task BODY dispatch is not yet wired. Listener-only bindings on user tasks
    // are allowed.
    if (!model.getModelElementsByType(UserTask.class).isEmpty()
        && serviceTaskBindingHitsUserTask(model, bindings)) {
      throw new UnsupportedOperationException("user-task lambda dispatch is not yet supported");
    }

    warnAboutUnboundServiceTasks(model, bindings.keySet());

    final String runId = RunIdGenerator.generate();
    final String user = System.getProperty("user.name", "user");
    final String script = callerSimpleName();

    final ModelRewriter.Rewritten rewritten =
        ModelRewriter.rewrite(model, runId, bindings.keySet());

    final CamundaClient client = cluster.client();

    LOG.info("deploying processId={} (runId={})", rewritten.prefixedProcessId(), runId);
    final DeploymentEvent deployment =
        client
            .newDeployResourceCommand()
            .addProcessModel(rewritten.model(), rewritten.prefixedProcessId() + ".bpmn")
            .send()
            .join();
    final Process deployedProcess =
        deployment.getProcesses().stream()
            .filter(p -> rewritten.prefixedProcessId().equals(p.getBpmnProcessId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "deployment did not include process " + rewritten.prefixedProcessId()));
    final long processDefinitionKey = deployedProcess.getProcessDefinitionKey();
    LOG.info("deployed processDefinitionKey={}", processDefinitionKey);

    final WorkerRegistration workers = new WorkerRegistration();
    workers.register(client, bindings, rewritten.jobTypesByBinding(), runId);

    // Brief wait so worker subscriptions are active before the first instance is created.
    LockSupport.parkNanos(java.time.Duration.ofMillis(150).toNanos());

    final Instant startedAt = Instant.now();
    final List<Long> instances = new ArrayList<>(instanceCount);
    final String[] baseTags = new String[] {"runId:" + runId, "user:" + user, "script:" + script};
    final String[] extraTags = opts.extraTags();
    final String[] allTags = new String[baseTags.length + extraTags.length];
    System.arraycopy(baseTags, 0, allTags, 0, baseTags.length);
    System.arraycopy(extraTags, 0, allTags, baseTags.length, extraTags.length);

    final java.time.Duration pacing = opts.pacingOrNull();
    for (int i = 0; i < instanceCount; i++) {
      final Map<String, Object> vars = opts.variablesFor(i);
      var step =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .tags(allTags);
      if (vars != null && !vars.isEmpty()) {
        step = step.variables(vars);
      }
      final ProcessInstanceEvent event = step.send().join();
      instances.add(event.getProcessInstanceKey());
      if (pacing != null && i < instanceCount - 1) {
        LockSupport.parkNanos(pacing.toNanos());
      }
    }
    LOG.info("created {} instance(s) for runId={}", instances.size(), runId);

    final Map<String, String> jobTypeToHandleKey = new LinkedHashMap<>();
    rewritten
        .jobTypesByBinding()
        .forEach((key, jobType) -> jobTypeToHandleKey.put(jobType, key.handleKey()));

    final DefaultRun run =
        new DefaultRun(
            runId,
            rewritten.prefixedProcessId(),
            processDefinitionKey,
            startedAt,
            instances,
            cluster,
            workers,
            cluster.restAddress(),
            jobTypeToHandleKey);
    // DefaultRun has already logged the Operate URL (and may have auto-opened the browser).
    return run;
  }

  private static boolean serviceTaskBindingHitsUserTask(
      final BpmnModelInstance model, final Map<BindingKey, BoundHandler> bindings) {
    for (final BindingKey key : bindings.keySet()) {
      if (key.kind() != BindingKind.SERVICE_TASK) {
        continue;
      }
      if (model.getModelElementById(key.elementId()) instanceof UserTask) {
        return true;
      }
    }
    return false;
  }

  /**
   * Logs a warning for every service task in the model that has no LiveBpmn binding. Two cases:
   *
   * <ul>
   *   <li>Task with {@code zeebeJobType} set but no binding → warn (assuming the user wants
   *       LiveBpmn to handle it but forgot to {@code .bind(elementId, lambda)}; or assumes an
   *       external worker — both are plausible, hence a warning rather than a hard error).
   *   <li>Task with no {@code zeebeJobType} at all → louder warning (the broker will reject this;
   *       the process can never make progress).
   * </ul>
   */
  private static void warnAboutUnboundServiceTasks(
      final BpmnModelInstance model, final java.util.Set<BindingKey> boundKeys) {
    final java.util.Set<String> boundServiceTaskIds = new java.util.HashSet<>();
    for (final BindingKey key : boundKeys) {
      if (key.kind() == BindingKind.SERVICE_TASK) {
        boundServiceTaskIds.add(key.elementId());
      }
    }
    for (final ServiceTask task : model.getModelElementsByType(ServiceTask.class)) {
      final String elementId = task.getId();
      if (boundServiceTaskIds.contains(elementId)) {
        continue;
      }
      final ZeebeTaskDefinition def = task.getSingleExtensionElement(ZeebeTaskDefinition.class);
      final String jobType = def == null ? null : def.getType();
      if (jobType == null || jobType.isBlank()) {
        LOG.warn(
            "service task '{}' has no zeebeJobType and no binding — this process will get stuck."
                + " Did you forget to call .bind(\"{}\", lambda)?",
            elementId,
            elementId);
      } else {
        LOG.warn(
            "service task '{}' (jobType '{}') has no LiveBpmn binding — assuming an external"
                + " worker will handle it. Call .bind(\"{}\", lambda) to handle it here.",
            elementId,
            jobType,
            elementId);
      }
    }
  }

  private static String callerSimpleName() {
    final StackTraceElement[] trace = Thread.currentThread().getStackTrace();
    for (final StackTraceElement frame : trace) {
      final String cls = frame.getClassName();
      if (!cls.startsWith("java.")
          && !cls.startsWith("jdk.")
          && !cls.startsWith("io.camunda.runner.")
          && !cls.equals("java.lang.Thread")) {
        final int dot = cls.lastIndexOf('.');
        return dot < 0 ? cls : cls.substring(dot + 1);
      }
    }
    return "unknown";
  }
}
