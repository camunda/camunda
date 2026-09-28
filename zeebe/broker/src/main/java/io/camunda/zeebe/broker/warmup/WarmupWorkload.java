/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.warmup;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCompleteJobRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeployResourceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * Drives the bundled warm-up process through a {@link ScratchEngine} the way clients would: every
 * command enters as an encoded gateway request, streamed jobs are completed as they are pushed,
 * polled jobs are activated and completed, each instance is sent a correlating message, and the
 * process is redeployed as a new version every so often. It runs entirely on the calling thread,
 * keeping at most a fixed number of instances in flight.
 */
@NullMarked
final class WarmupWorkload {

  static final String PROCESS_ID = "leader-warmup";
  static final String STREAMED_JOB_TYPE = "leader-warmup-streamed";
  static final Map<String, Object> CLAIMS = Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, true);

  private static final String RESOURCE = "leader-warmup/leader-warmup.bpmn";
  private static final String POLLED_JOB_TYPE = "leader-warmup-polled";
  private static final String MESSAGE_NAME = "leader-warmup-confirmation";
  private static final String FINAL_ELEMENT = "finish";
  private static final int WITH_RESULT_EVERY = 4;
  private static final int REDEPLOY_EVERY = 100;
  private static final String PROCESS_ELEMENT =
      "<bpmn:process id=\"leader-warmup\" isExecutable=\"true\">";
  private static final Duration POLL_INTERVAL = Duration.ofMillis(50);
  private static final Duration BACK_OFF = Duration.ofSeconds(1);
  private static final Duration STALL_TIMEOUT = Duration.ofSeconds(30);

  private final ScratchServerTransport transport;
  private final int processInstances;
  private final int maxInFlight;
  private final LinkedBlockingQueue<Runnable> events = new LinkedBlockingQueue<>();
  private final Map<Long, Pending<?>> pendingRequests = new HashMap<>();
  private final Map<Long, String> orderIdByInstance = new HashMap<>();
  private final ArrayDeque<Retry> retries = new ArrayDeque<>();
  private final String resource;

  private int started;
  private int inFlight;
  private int completed;
  private int rejected;
  private long commandsSent;
  private long lastRequestId;
  private boolean activationInFlight;
  private long nextActivationNanos;

  WarmupWorkload(final int processInstances, final int maxInFlight) {
    transport = new ScratchServerTransport(this::onResponse);
    this.processInstances = processInstances;
    this.maxInFlight = maxInFlight;
    resource = readResource();
  }

  /** Called from the scratch transport, on a scratch actor thread. */
  private void onResponse(final long requestId, final DirectBuffer response) {
    events.add(() -> handleResponse(requestId, response));
  }

  /** Called from the scratch job streamer, on the scratch stream processor thread. */
  void onJobPushed(final ActivatedJob job) {
    final var jobKey = job.jobKey();
    final var elementId = job.jobRecord().getElementId();
    final var processInstanceKey = job.jobRecord().getProcessInstanceKey();
    events.add(() -> completeJob(jobKey, elementId, processInstanceKey));
  }

  /**
   * Runs the workload until the configured number of instances has completed, {@code shouldStop}
   * returns true, or {@code deadline} passes. While {@code underPressure} returns true, no new
   * instances are started.
   */
  Outcome run(
      final BooleanSupplier shouldStop, final BooleanSupplier underPressure, final long deadline)
      throws InterruptedException {
    final var deployed = new boolean[1];
    deploy(0, response -> deployed[0] = true);

    long lastProgress = System.nanoTime();
    while (true) {
      if (shouldStop.getAsBoolean()) {
        return Outcome.CANCELLED;
      }
      if (completed >= processInstances) {
        return Outcome.COMPLETED;
      }
      final var now = System.nanoTime();
      if (now - deadline > 0) {
        return Outcome.TIMED_OUT;
      }
      if (now - lastProgress > STALL_TIMEOUT.toNanos()) {
        throw new IllegalStateException(
            "Warm-up made no progress for %s (%d instances in flight, %d rejections)"
                .formatted(STALL_TIMEOUT, inFlight, rejected));
      }

      while (!retries.isEmpty() && now - retries.peek().dueNanos() > 0) {
        retries.poll().send().run();
      }

      final var pressure = underPressure.getAsBoolean();
      if (deployed[0] && !pressure) {
        while (inFlight < maxInFlight && started < processInstances) {
          startInstance();
        }
        if (!activationInFlight && inFlight > 0 && now - nextActivationNanos > 0) {
          activateJobs();
        }
      }

      final var event =
          events.poll(
              pressure ? BACK_OFF.toMillis() : POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
      if (event != null) {
        event.run();
        lastProgress = System.nanoTime();
      } else if (pressure || !retries.isEmpty()) {
        lastProgress = System.nanoTime();
      }
    }
  }

  ScratchServerTransport transport() {
    return transport;
  }

  long commandsSent() {
    return commandsSent;
  }

  int completedInstances() {
    return completed;
  }

  /**
   * Each revision is a new process version, which the engine and the exporters parse as a real
   * deployment.
   */
  private void deploy(final int revision, final Consumer<Object> onDeployed) {
    final var revised =
        resource.replace(
            PROCESS_ELEMENT,
            PROCESS_ELEMENT.replace(
                ">", " name=\"Leader warm-up revision %d\">".formatted(revision)));
    send(
        new BrokerDeployResourceRequest()
            .addResource("leader-warmup.bpmn", revised.getBytes(StandardCharsets.UTF_8)),
        onDeployed::accept);
  }

  private void startInstance() {
    final var index = started++;
    inFlight++;
    if (index > 0 && index % REDEPLOY_EVERY == 0) {
      deploy(index / REDEPLOY_EVERY, response -> {});
    }
    final var orderId = "order-" + index;
    final var variables = BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(payload(index)));

    if (index % WITH_RESULT_EVERY == 0) {
      // The instance's key only arrives once it has completed, so publish up front and let the
      // message wait in the buffer for the subscription.
      send(
          new BrokerCreateProcessInstanceWithResultRequest()
              .setBpmnProcessId(PROCESS_ID)
              .setVariables(variables),
          response -> {});
      publishMessage(orderId);
    } else {
      send(
          new BrokerCreateProcessInstanceRequest()
              .setBpmnProcessId(PROCESS_ID)
              .setVariables(variables),
          response -> onInstanceCreated(response, orderId));
    }
  }

  private void onInstanceCreated(final ProcessInstanceCreationRecord response, final String id) {
    orderIdByInstance.put(response.getProcessInstanceKey(), id);
  }

  private void activateJobs() {
    activationInFlight = true;
    send(
        new BrokerActivateJobsRequest(POLLED_JOB_TYPE)
            .setMaxJobsToActivate(maxInFlight)
            .setTimeout(Duration.ofMinutes(5).toMillis())
            .setWorker("leader-warmup"),
        this::onJobsActivated);
  }

  private void onJobsActivated(final JobBatchRecord batch) {
    activationInFlight = false;
    final var keys = batch.getJobKeys();
    final var jobs = batch.getJobs();
    if (keys.isEmpty()) {
      nextActivationNanos = System.nanoTime() + POLL_INTERVAL.toNanos();
    }
    for (int i = 0; i < keys.size(); i++) {
      final var job = jobs.get(i);
      completeJob(keys.get(i), job.getElementId(), job.getProcessInstanceKey());
    }
  }

  private void completeJob(
      final long jobKey, final String elementId, final long processInstanceKey) {
    final var variables =
        switch (elementId) {
          case "prepare" -> "{\"prepared\":true}";
          case FINAL_ELEMENT -> "{\"done\":true}";
          default -> "{\"approved\":true,\"reviewer\":\"leader-warmup\"}";
        };
    send(
        new BrokerCompleteJobRequest(
            jobKey,
            BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(variables)),
            new JobResult()),
        response -> onJobCompleted(elementId, processInstanceKey));
  }

  private void onJobCompleted(final String elementId, final long processInstanceKey) {
    switch (elementId) {
      case "approve", "review" -> {
        final var orderId = orderIdByInstance.get(processInstanceKey);
        if (orderId != null) {
          publishMessage(orderId);
        }
      }
      case FINAL_ELEMENT -> {
        orderIdByInstance.remove(processInstanceKey);
        inFlight--;
        completed++;
      }
      default -> {}
    }
  }

  private void publishMessage(final String orderId) {
    send(
        new BrokerPublishMessageRequest(MESSAGE_NAME, orderId)
            .setTimeToLive(Duration.ofMinutes(1).toMillis())
            .setVariables(
                BufferUtil.wrapArray(
                    MsgPackConverter.convertToMsgPack(
                        "{\"confirmed\":true,\"channel\":\"email\"}"))),
        response -> {});
  }

  private <T> void send(final BrokerExecuteCommand<T> request, final Consumer<T> onSuccess) {
    request.setPartitionId(ScratchEngine.PARTITION_ID);
    request.setAuthorization(CLAIMS);
    request.serializeValue();
    final var bytes = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(bytes, 0);

    final var requestId = ++lastRequestId;
    pendingRequests.put(requestId, new Pending<>(request, onSuccess));
    if (!transport.sendCommand(ScratchEngine.PARTITION_ID, requestId, bytes)) {
      throw new IllegalStateException("Scratch engine is not accepting commands");
    }
    commandsSent++;
  }

  private void handleResponse(final long requestId, final DirectBuffer buffer) {
    final var pending = pendingRequests.remove(requestId);
    if (pending != null) {
      pending.complete(buffer);
    }
  }

  private <T> void onFailure(
      final BrokerExecuteCommand<T> request,
      final Consumer<T> onSuccess,
      final BrokerResponse<T> response) {
    if (request instanceof BrokerActivateJobsRequest) {
      activationInFlight = false;
      nextActivationNanos = System.nanoTime() + POLL_INTERVAL.toNanos();
      return;
    }
    if (response.isError()) {
      // Errors such as back pressure mean the command was never written, so it is safe to resend.
      retries.add(
          new Retry(System.nanoTime() + POLL_INTERVAL.toNanos(), () -> send(request, onSuccess)));
      return;
    }
    rejected++;
    if (rejected > processInstances / 100 + 10) {
      throw new IllegalStateException(
          "Warm-up commands are being rejected, last: %s".formatted(response));
    }
  }

  private static String payload(final int index) {
    final var amount = (index * 37) % 1000;
    final var items = new StringBuilder();
    for (int i = 0; i < 5; i++) {
      if (i > 0) {
        items.append(',');
      }
      items
          .append("{\"sku\":\"SKU-")
          .append(index % 97)
          .append('-')
          .append(i)
          .append("\",\"price\":")
          .append((index + i) % 200 + 0.99)
          .append(",\"quantity\":")
          .append(i + 1)
          .append('}');
    }
    return """
        {"orderId":"order-%d","amount":%d,"customer":{"name":"Customer %d","tier":"%s",\
        "address":{"street":"%d Main Street","city":"springfield","country":"GB"}},\
        "items":[%s],"notes":"%s"}"""
        .formatted(
            index,
            amount,
            index % 1000,
            amount > 700 ? "gold" : "standard",
            index % 500,
            items,
            "leader warm-up order ".repeat(8));
  }

  private static String readResource() {
    try (final InputStream stream =
        Objects.requireNonNull(
            WarmupWorkload.class.getClassLoader().getResourceAsStream(RESOURCE),
            "Missing warm-up resource " + RESOURCE)) {
      final var bpmn = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      if (!bpmn.contains(PROCESS_ELEMENT)) {
        throw new IllegalStateException("Warm-up resource has no process element to revise");
      }
      return bpmn;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private record Retry(long dueNanos, Runnable send) {}

  enum Outcome {
    COMPLETED,
    CANCELLED,
    TIMED_OUT
  }

  private final class Pending<T> {
    private final BrokerExecuteCommand<T> request;
    private final Consumer<T> onSuccess;

    private Pending(final BrokerExecuteCommand<T> request, final Consumer<T> onSuccess) {
      this.request = request;
      this.onSuccess = onSuccess;
    }

    private void complete(final DirectBuffer buffer) {
      final BrokerResponse<T> response = request.getResponse(buffer);
      if (response.isResponse()) {
        onSuccess.accept(response.getResponse());
      } else {
        onFailure(request, onSuccess, response);
      }
    }
  }
}
