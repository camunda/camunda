/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.core.ResolvableType;

public class Processing {
  private static final String PREFIX = "camunda.processing";
  private static final int DEFAULT_PROCESSING_BATCH_LIMIT = 100;
  private static final boolean DEFAULT_ENABLE_ASYNC_SCHEDULED_TASKS = true;
  private static final Duration DEFAULT_SCHEDULED_TASKS_CHECK_INTERVAL = Duration.ofSeconds(1);

  private static final boolean DEFAULT_ENABLE_PRECONDITIONS_CHECK = false;
  private static final boolean DEFAULT_ENABLE_FOREIGN_KEY_CHECKS = false;

  private static final boolean DEFAULT_ENABLE_YIELDING_DUEDATE_CHECKER = true;
  private static final boolean DEFAULT_ENABLE_ASYNC_MESSAGE_TTL_CHECKER = false;
  private static final boolean DEFAULT_ENABLE_ASYNC_TIMER_DUEDATE_CHECKER = false;
  private static final boolean DEFAULT_ENABLE_STRAIGHTTHROUGH_PROCESSING_LOOP_DETECTOR = true;
  private static final boolean DEFAULT_ENABLE_MESSAGE_BODY_ON_EXPIRED = false;

  private static final Set<String> LEGACY_MAX_COMMANDS_IN_BATCH_PROPERTIES =
      Set.of("zeebe.broker.processingCfg.maxCommandsInBatch");
  private static final Set<String> LEGACY_ENABLE_ASYNC_SCHEDULED_TASKS_PROPERTIES =
      Set.of("zeebe.broker.processingCfg.enableAsyncScheduledTasks");
  private static final Set<String> LEGACY_SCHEDULED_TASKS_CHECK_INTERVAL_PROPERTIES =
      Set.of("zeebe.broker.processingCfg.scheduledTaskCheckInterval");
  private static final Set<String> LEGACY_SKIP_POSITIONS_PROPERTIES =
      Set.of("zeebe.broker.processingCfg.skipPositions");

  private static final Set<String> LEGACY_CONSISTENCY_CHECK_ENABLE_PRECONDITIONS_PROPERTIES =
      Set.of("zeebe.broker.experimental.consistencyChecks.enablePreconditions");
  private static final Set<String> LEGACY_CONSISTENCY_CHECK_ENABLE_FOREIGN_KEY_CHECKS_PROPERTIES =
      Set.of("zeebe.broker.experimental.consistencyChecks.enableForeignKeyChecks");

  private static final Set<String> LEGACY_FEATURES_ENABLE_YIELD_DUEDATE_CHECKER_PROPERTIES =
      Set.of("zeebe.broker.experimental.features.enableYieldingDueDateChecker");
  private static final Set<String> LEGACY_FEATURES_ENABLE_MESSAGE_TTL_CHECKER_ASYNC_PROPERTIES =
      Set.of("zeebe.broker.experimental.features.enableMessageTtlCheckerAsync");
  private static final Set<String> LEGACY_FEATURES_ENABLE_TIMER_DUE_DATE_CHECKER_ASYNC_PROPERTIES =
      Set.of("zeebe.broker.experimental.features.enableTimerDueDateCheckerAsync");
  private static final Set<String>
      LEGACY_FEATURES_ENABLE_STRAIGHT_THROUGH_PROCESSING_LOOP_DETECTOR_PROPERTIES =
          Set.of("zeebe.broker.experimental.features.enableStraightThroughProcessingLoopDetector");
  private static final Set<String> LEGACY_FEATURES_ENABLE_MESSAGE_BODY_ON_EXPIRED_PROPERTIES =
      Set.of("zeebe.broker.experimental.features.enableMessageBodyOnExpired");

  /**
   * Configure flow control for user requests. This setting takes precedence over the backpressure
   * configuration.
   */
  @NestedConfigurationProperty FlowControl flowControl = new FlowControl();

  /**
   * Sets the maximum number of commands that processed within one batch. The processor will process
   * until no more follow up commands are created by the initial command or the configured limit is
   * reached. By default, up to 100 commands are processed in one batch. Can be set to 1 to disable
   * batch processing. Must be a positive integer number. Note that the resulting batch size will
   * contain more entries than this limit because it includes follow up events. When resulting batch
   * size is too large (see maxMessageSize), processing will be rolled back and retried with a
   * smaller maximum batch size. Lowering the command limit can reduce the frequency of rollback and
   * retry.
   */
  private Integer maxCommandsInBatch = DEFAULT_PROCESSING_BATCH_LIMIT;

  /**
   * Allows scheduled processing tasks such as checking for timed-out jobs to run concurrently to
   * regular processing. This is a performance optimization to ensure that processing is not
   * interrupted by higher than usual workload for any of the scheduled tasks. This should only be
   * disabled in case of bugs, for example if one of the scheduled tasks is not safe to run
   * concurrently to regular processing.
   *
   * <p>This replaces the deprecated experimental settings that enable async scheduling for specific
   * tasks only, for example `enableMessageTTLCheckerAsync`. When `enableAsyncScheduledTasks` is
   * enabled (which it is by default), the deprecated settings take no effect. When
   * `enableAsyncScheduledTasks` is disabled, scheduled tasks are only run async if explicitly
   * enabled by the deprecated setting.
   */
  private boolean enableAsyncScheduledTasks = DEFAULT_ENABLE_ASYNC_SCHEDULED_TASKS;

  /**
   * Configures the rate at which a partition leader checks for expired scheduled tasks such as the
   * due date checker. The default value is 1 second. Use a lower interval to potentially decrease
   * delays between requested and actual execution of scheduled tasks. Using a low interval will
   * result in unnecessary load while idle. We recommend to benchmark any changes to this setting.
   */
  private Duration scheduledTasksCheckInterval = DEFAULT_SCHEDULED_TASKS_CHECK_INTERVAL;

  /**
   * Allows to skip certain commands by their position. This is useful for debugging and data
   * recovery. It is not recommended to use this in production. The value is a comma-separated list
   * of positions to skip. Whitespace is ignored.
   */
  private Set<Long> skipPositions;

  /**
   * Configures if the basic operations on RocksDB, such as inserting or deleting key-value pairs,
   * should check preconditions, for example that a key does not already exist when inserting.
   */
  private boolean enablePreconditionsCheck = DEFAULT_ENABLE_PRECONDITIONS_CHECK;

  /**
   * Configures if inserting or updating key-value pairs on RocksDB should check that foreign keys
   * exist.
   */
  private boolean enableForeignKeyChecks = DEFAULT_ENABLE_FOREIGN_KEY_CHECKS;

  /**
   * Changes the DueDateTimerChecker to give yield to other processing steps in situations where it
   * has many (i.e. millions of) timers to process. If set to false (default) the
   * DueDateTimerChecker will activate all due timers. In the worst case, this can lead to the node
   * being blocked for indefinite amount of time, being subsequently flagged as unhealthy.
   * Currently, there is no known way to recover from this situation If set to true, the
   * DueDateTimerChecker will give yield to other processing steps. This avoids the worst case
   * described above. However, under consistent high load it may happen that the activated timers
   * will fall behind real time, if more timers become due than can be activated during a certain
   * time period.
   */
  private boolean enableYieldingDueDateChecker = DEFAULT_ENABLE_YIELDING_DUEDATE_CHECKER;

  /**
   * While disabled, checking the Time-To-Live of buffered messages blocks all other executions that
   * occur on the stream processor, including process execution and job activation/completion. When
   * enabled, the Message TTL Checker will run asynchronous to the Engine's stream processor. This
   * helps improve throughput and process latency in use cases that publish many messages with a
   * non-zero TTL. We recommend testing this feature in a non-production environment before enabling
   * it in production.
   */
  private boolean enableAsyncMessageTtlChecker = DEFAULT_ENABLE_ASYNC_MESSAGE_TTL_CHECKER;

  /**
   * While disabled, checking for due timers blocks all other executions that occur on the stream
   * processor, including process execution and job activation/completion. When enabled, the Due
   * Date Checker will run asynchronous to the Engine's stream processor. This helps improve
   * throughput and process latency when there are a lot of timers. We recommend testing this
   * feature in a non-production environment before enabling it in production.
   */
  private boolean enableAsyncTimerDuedateChecker = DEFAULT_ENABLE_ASYNC_TIMER_DUEDATE_CHECKER;

  private boolean enableStraightthroughProcessingLoopDetector =
      DEFAULT_ENABLE_STRAIGHTTHROUGH_PROCESSING_LOOP_DETECTOR;

  /**
   * Controls whether the full message body is included in the follow-up event when a message
   * expires. When enabled, the system appends the entire message payload during expiration. This is
   * useful in environments where full visibility into expired messages is needed. Such that full
   * message details can be exported by configuring the exporter filtering to allow
   * `Message.EXPIRED` events. However, including the full message body increases the size of each
   * follow-up event. For large messages (e.g., ~100KB), this may lead to batch size limits being
   * exceeded earlier. As a result, fewer messages may be expired per batch (e.g., only 40 instead
   * of more), which requires multiple checker runs to process the full batch. Please be aware that
   * this may introduce performance regressions or cause the expired message state to grow more
   * quickly over time. To maintain backward compatibility and avoid performance degradation, the
   * default value is false, meaning message bodies will not be appended unless explicitly enabled.
   */
  private boolean enableMessageBodyOnExpired = DEFAULT_ENABLE_MESSAGE_BODY_ON_EXPIRED;

  public Integer getMaxCommandsInBatch() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".max-commands-in-batch",
        maxCommandsInBatch,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_COMMANDS_IN_BATCH_PROPERTIES);
  }

  public void setMaxCommandsInBatch(final Integer maxCommandsInBatch) {
    this.maxCommandsInBatch = maxCommandsInBatch;
  }

  public boolean isEnableAsyncScheduledTasks() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".enable-async-scheduled-tasks",
        enableAsyncScheduledTasks,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ENABLE_ASYNC_SCHEDULED_TASKS_PROPERTIES);
  }

  public void setEnableAsyncScheduledTasks(final boolean enableAsyncScheduledTasks) {
    this.enableAsyncScheduledTasks = enableAsyncScheduledTasks;
  }

  public Duration getScheduledTasksCheckInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".scheduled-tasks-check-interval",
        scheduledTasksCheckInterval,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_SCHEDULED_TASKS_CHECK_INTERVAL_PROPERTIES);
  }

  public void setScheduledTasksCheckInterval(final Duration scheduledTasksCheckInterval) {
    this.scheduledTasksCheckInterval = scheduledTasksCheckInterval;
  }

  public Set<Long> getSkipPositions() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".skip-positions",
        skipPositions,
        ResolvableType.forClassWithGenerics(Set.class, Long.class),
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_SKIP_POSITIONS_PROPERTIES);
  }

  public void setSkipPositions(final Set<Long> skipPositions) {
    this.skipPositions = skipPositions;
  }

  public boolean isEnablePreconditionsCheck() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".enable-preconditions-check",
        enablePreconditionsCheck,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_CONSISTENCY_CHECK_ENABLE_PRECONDITIONS_PROPERTIES);
  }

  public void setEnablePreconditionsCheck(final boolean enablePreconditionsCheck) {
    this.enablePreconditionsCheck = enablePreconditionsCheck;
  }

  public boolean isEnableForeignKeyChecks() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".enable-foreign-key-checks",
        enableForeignKeyChecks,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_CONSISTENCY_CHECK_ENABLE_FOREIGN_KEY_CHECKS_PROPERTIES);
  }

  public void setEnableForeignKeyChecks(final boolean enableForeignKeyChecks) {
    this.enableForeignKeyChecks = enableForeignKeyChecks;
  }

  public boolean isEnableYieldingDueDateChecker() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".enable-yielding-duedate-checker",
        enableYieldingDueDateChecker,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_FEATURES_ENABLE_YIELD_DUEDATE_CHECKER_PROPERTIES);
  }

  public void setEnableYieldingDueDateChecker(final boolean enableYieldingDueDateChecker) {
    this.enableYieldingDueDateChecker = enableYieldingDueDateChecker;
  }

  public boolean isEnableAsyncMessageTtlChecker() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".enable-async-message-ttl-checker",
        enableAsyncMessageTtlChecker,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_FEATURES_ENABLE_MESSAGE_TTL_CHECKER_ASYNC_PROPERTIES);
  }

  public void setEnableAsyncMessageTtlChecker(final boolean enableAsyncMessageTtlChecker) {
    this.enableAsyncMessageTtlChecker = enableAsyncMessageTtlChecker;
  }

  public boolean isEnableAsyncTimerDuedateChecker() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".enable-async-timer-duedate-checker",
        enableAsyncTimerDuedateChecker,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_FEATURES_ENABLE_TIMER_DUE_DATE_CHECKER_ASYNC_PROPERTIES);
  }

  public void setEnableAsyncTimerDuedateChecker(final boolean enableAsyncTimerDuedateChecker) {
    this.enableAsyncTimerDuedateChecker = enableAsyncTimerDuedateChecker;
  }

  public boolean isEnableStraightthroughProcessingLoopDetector() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".enable-straightthrough-processing-loop-detector",
        enableStraightthroughProcessingLoopDetector,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_FEATURES_ENABLE_STRAIGHT_THROUGH_PROCESSING_LOOP_DETECTOR_PROPERTIES);
  }

  public void setEnableStraightthroughProcessingLoopDetector(
      final boolean enableStraightthroughProcessingLoopDetector) {
    this.enableStraightthroughProcessingLoopDetector = enableStraightthroughProcessingLoopDetector;
  }

  public boolean isEnableMessageBodyOnExpired() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".enable-message-body-on-expired",
        enableMessageBodyOnExpired,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_FEATURES_ENABLE_MESSAGE_BODY_ON_EXPIRED_PROPERTIES);
  }

  public void setEnableMessageBodyOnExpired(final boolean enableMessageBodyOnExpired) {
    this.enableMessageBodyOnExpired = enableMessageBodyOnExpired;
  }

  public FlowControl getFlowControl() {
    return flowControl;
  }

  public void setFlowControl(final FlowControl flowControl) {
    this.flowControl = flowControl;
  }
}
