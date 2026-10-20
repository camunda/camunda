/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.starter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.StarterProperties;
import io.camunda.zeebe.metrics.StarterLatencyMetricsDoc;
import io.camunda.zeebe.metrics.StarterMetricsDoc;
import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.util.PayloadReader;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("starter")
public class ProcessInstanceCreator {

  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(
          LoggerFactory.getLogger(ProcessInstanceCreator.class), Duration.ofSeconds(5));
  private static final Logger LOG = LoggerFactory.getLogger(ProcessInstanceCreator.class);
  private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();
  private static final TypeReference<HashMap<String, Object>> VARIABLES_TYPE_REF =
      new TypeReference<>() {};
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final CamundaClient loaderClient;
  private final StarterProperties starterCfg;
  private final MeterRegistry registry;
  private final PayloadReader payloadReader;
  private final StarterLivenessIndicator livenessIndicator;
  private final List<StartListener> startListeners = new CopyOnWriteArrayList<>();
  private final AtomicLong businessKey = new AtomicLong(0);
  private final AtomicReference<Error> fatalError = new AtomicReference<>();

  private final Timer responseLatencyTimer;
  private final Counter processInstancesSubmittedCounter;
  private final Counter processInstancesSkippedCounter;
  private final StarterResultMetrics resultMetrics;

  public ProcessInstanceCreator(
      final CamundaClient loaderClient,
      final LoadTesterProperties properties,
      final MeterRegistry registry,
      final PayloadReader payloadReader,
      final StarterLivenessIndicator livenessIndicator) {
    this.loaderClient = loaderClient;
    starterCfg = properties.getStarter();
    this.registry = registry;
    this.payloadReader = payloadReader;
    this.livenessIndicator = livenessIndicator;

    responseLatencyTimer =
        MicrometerUtil.buildTimer(StarterLatencyMetricsDoc.RESPONSE_LATENCY).register(registry);
    processInstancesSubmittedCounter = registerSubmissionCounter("submitted");
    processInstancesSkippedCounter = registerSubmissionCounter("skipped");
    resultMetrics = new StarterResultMetrics(registry);
  }

  private Counter registerSubmissionCounter(final String outcome) {
    return Counter.builder(StarterMetricsDoc.PROCESS_INSTANCES_SUBMISSIONS.getName())
        .description(StarterMetricsDoc.PROCESS_INSTANCES_SUBMISSIONS.getDescription())
        .tag(StarterMetricKeyNames.OUTCOME.asString(), outcome)
        .register(registry);
  }

  public ScheduledFuture<?> schedule(
      final ScheduledExecutorService executorService, final CountDownLatch countDownLatch) {

    final long intervalNanos = (long) (NANOS_PER_SECOND / starterCfg.getRatePerSecond());
    LOG.info(
        "Creating an instance every {}ns (rate: {} per {})",
        intervalNanos,
        starterCfg.getRate(),
        starterCfg.getRateDuration());

    final String variablesString = payloadReader.readPayload(starterCfg.getPayloadPath());
    final Map<String, Object> baseVariables =
        Collections.unmodifiableMap(deserializeVariables(variablesString));

    final BooleanSupplier shouldContinue = createContinuationCondition();
    final Semaphore inFlight = new Semaphore(starterCfg.getMaxInFlightRequests());
    Gauge.builder(
            StarterMetricsDoc.PROCESS_INSTANCES_IN_FLIGHT.getName(),
            () -> starterCfg.getMaxInFlightRequests() - inFlight.availablePermits())
        .description(StarterMetricsDoc.PROCESS_INSTANCES_IN_FLIGHT.getDescription())
        .register(registry);

    return executorService.scheduleAtFixedRate(
        () -> {
          if (!shouldContinue.getAsBoolean()) {
            countDownLatch.countDown();
            return;
          }

          if (!inFlight.tryAcquire()) {
            processInstancesSkippedCounter.increment();
            THROTTLED_LOGGER.debug(
                "Skipping process instance creation, {} requests are already in flight",
                starterCfg.getMaxInFlightRequests());
            return;
          }

          final var startTime = System.nanoTime();
          final CompletionStage<?> requestFuture;
          try {
            final var vars = new HashMap<>(baseVariables);
            vars.put(starterCfg.getBusinessKey(), businessKey.incrementAndGet());

            if (starterCfg.isStartViaMessage()) {
              requestFuture = startInstanceByMessagePublishing(vars);
            } else if (starterCfg.isWithResults()) {
              requestFuture = startInstanceWithAwaitingResult(starterCfg.getProcessId(), vars);
            } else {
              requestFuture = startInstance(startTime, starterCfg.getProcessId(), vars);
            }
            processInstancesSubmittedCounter.increment();
          } catch (final Exception e) {
            inFlight.release();
            THROTTLED_LOGGER.error("Error on creating new process instance", e);
            return;
          } catch (final Error e) {
            // An Error escaping a scheduleAtFixedRate task silently cancels all future runs, so
            // stop the starter and fail the application instead.
            LOG.error("Fatal error on creating new process instance, stopping the starter", e);
            fatalError.set(e);
            countDownLatch.countDown();
            return;
          }

          requestFuture.whenComplete(
              (noop, error) -> {
                inFlight.release();
                livenessIndicator.recordResult(error);
                final long durationNanos = System.nanoTime() - startTime;
                responseLatencyTimer.record(durationNanos, TimeUnit.NANOSECONDS);
                resultMetrics.record(error);
                if (error instanceof final StatusRuntimeException statusRuntimeException) {
                  if (statusRuntimeException.getStatus().getCode() != Code.RESOURCE_EXHAUSTED) {
                    THROTTLED_LOGGER.warn(
                        "Error on creating new process instance with business key {}",
                        businessKey.get(),
                        error);
                  }
                }
              });
        },
        0,
        intervalNanos,
        TimeUnit.NANOSECONDS);
  }

  private CompletionStage<ProcessInstanceEvent> startInstance(
      final long startTime, final String processId, final HashMap<String, Object> variables) {
    return loaderClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .send()
        .thenApply(
            (response) -> {
              startListeners.forEach(
                  listener -> listener.onStarted(response.getProcessInstanceKey(), startTime));
              return response;
            });
  }

  private CompletionStage<?> startInstanceWithAwaitingResult(
      final String processId, final HashMap<String, Object> variables) {
    return loaderClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .withResult()
        .requestTimeout(starterCfg.getWithResultsTimeout())
        .send();
  }

  private CompletionStage<?> startInstanceByMessagePublishing(final Map<String, Object> variables) {
    return loaderClient
        .newPublishMessageCommand()
        .messageName(starterCfg.getMsgName())
        .correlationKey(UUID.randomUUID().toString())
        .variables(variables)
        .timeToLive(Duration.ZERO)
        .send();
  }

  private static HashMap<String, Object> deserializeVariables(final String variablesString) {
    try {
      return OBJECT_MAPPER.readValue(variablesString, VARIABLES_TYPE_REF);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to parse variables '{}'.", variablesString, e);
      throw new RuntimeException(e);
    }
  }

  private BooleanSupplier createContinuationCondition() {
    final int durationLimit = starterCfg.getDurationLimit();

    if (durationLimit > 0) {
      final LocalDateTime endTime = LocalDateTime.now().plus(durationLimit, ChronoUnit.SECONDS);
      return () -> LocalDateTime.now().isBefore(endTime);
    } else {
      return () -> true;
    }
  }

  public long currentBusinessKey() {
    return businessKey.get();
  }

  public long submittedCount() {
    return (long) processInstancesSubmittedCounter.count();
  }

  public Error fatalError() {
    return fatalError.get();
  }

  /** Registers a {@link StartListener} that is called for each started process instance. */
  public void addStartListener(final StartListener listener) {
    startListeners.add(listener);
  }

  /**
   * Callback that other components can register with {@link #addStartListener}. It is called each
   * time a process instance was started with a create instance command, with the key of the
   * instance and the {@link System#nanoTime()} value at which the start request was sent. It is not
   * called when instances are started by message or with results.
   */
  @FunctionalInterface
  public interface StartListener {
    void onStarted(long processInstanceKey, long startTimeNanos);
  }
}
