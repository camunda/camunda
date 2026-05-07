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
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import java.time.Instant;
import java.util.ArrayList;
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
      final Map<String, BoundHandler> bindings,
      final int instanceCount,
      final Cluster cluster) {
    if (instanceCount < 0) {
      throw new IllegalArgumentException("instanceCount must be >= 0, got " + instanceCount);
    }

    // Defensive: user-task lambda dispatch is not yet wired.
    if (!model.getModelElementsByType(UserTask.class).isEmpty()
        && bindingsContainUserTasks(model, bindings)) {
      throw new UnsupportedOperationException("user-task lambda dispatch is not yet supported");
    }

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
    workers.register(client, bindings, rewritten.jobTypesByElementId(), runId);

    // Brief wait so worker subscriptions are active before the first instance is created.
    LockSupport.parkNanos(java.time.Duration.ofMillis(150).toNanos());

    final Instant startedAt = Instant.now();
    final List<Long> instances = new ArrayList<>(instanceCount);
    final String[] tags = new String[] {"runId:" + runId, "user:" + user, "script:" + script};
    for (int i = 0; i < instanceCount; i++) {
      final ProcessInstanceEvent event =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .tags(tags)
              .send()
              .join();
      instances.add(event.getProcessInstanceKey());
    }
    LOG.info("created {} instance(s) for runId={}", instances.size(), runId);

    return new DefaultRun(
        runId,
        rewritten.prefixedProcessId(),
        processDefinitionKey,
        startedAt,
        instances,
        cluster,
        workers);
  }

  private static boolean bindingsContainUserTasks(
      final BpmnModelInstance model, final Map<String, BoundHandler> bindings) {
    for (final String id : bindings.keySet()) {
      if (model.getModelElementById(id) instanceof UserTask) {
        return true;
      }
    }
    return false;
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
