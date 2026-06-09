/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

/**
 * Predefined {@link WaitStateTransformerConfig}s for common use cases, e.g. for jobs. These can be
 * used as a base for custom {@link WaitStateTransformerConfig}s, e.g. by using {@link
 * WaitStateTransformerConfig#withAddIntents(Intent...)}, {@link
 * WaitStateTransformerConfig#withRemoveIntents(Intent...)}, and {@link
 * WaitStateTransformerConfig#withWaitStateType(WaitStateType)} to customize them.
 */
public final class WaitStateConfigs {

  public static final WaitStateTransformerConfig JOB_CONFIG =
      WaitStateTransformerConfig.of(ValueType.JOB)
          .withAddIntents(JobIntent.CREATED)
          .withUpdateIntents(JobIntent.MIGRATED)
          .withRemoveIntents(JobIntent.COMPLETED, JobIntent.CANCELED)
          .withWaitStateType(WaitStateType.JOB);

  public static final WaitStateTransformerConfig USER_TASK_CONFIG =
      WaitStateTransformerConfig.of(ValueType.USER_TASK)
          .withAddIntents(UserTaskIntent.CREATED)
          .withUpdateIntents(UserTaskIntent.MIGRATED, UserTaskIntent.UPDATED)
          .withRemoveIntents(UserTaskIntent.COMPLETED, UserTaskIntent.CANCELED)
          .withWaitStateType(WaitStateType.USER_TASK);

  public static final WaitStateTransformerConfig TIMER_CONFIG =
      WaitStateTransformerConfig.of(ValueType.TIMER)
          .withAddIntents(TimerIntent.CREATED)
          .withUpdateIntents(TimerIntent.MIGRATED)
          .withRemoveIntents(TimerIntent.TRIGGERED, TimerIntent.CANCELED)
          .withWaitStateType(WaitStateType.TIMER);

  public static final WaitStateTransformerConfig MESSAGE_CONFIG =
      WaitStateTransformerConfig.of(ValueType.MESSAGE_SUBSCRIPTION)
          .withAddIntents(MessageSubscriptionIntent.CREATED)
          .withUpdateIntents(MessageSubscriptionIntent.MIGRATED)
          .withRemoveIntents(
              MessageSubscriptionIntent.CORRELATED, MessageSubscriptionIntent.DELETED)
          .withWaitStateType(WaitStateType.MESSAGE);

  public static final WaitStateTransformerConfig SIGNAL_CONFIG =
      WaitStateTransformerConfig.of(ValueType.SIGNAL_SUBSCRIPTION)
          .withAddIntents(SignalSubscriptionIntent.CREATED)
          .withUpdateIntents(SignalSubscriptionIntent.MIGRATED)
          .withRemoveIntents(SignalSubscriptionIntent.DELETED)
          .withWaitStateType(WaitStateType.SIGNAL);

  public static final WaitStateTransformerConfig CONDITION_CONFIG =
      WaitStateTransformerConfig.of(ValueType.CONDITIONAL_SUBSCRIPTION)
          .withAddIntents(ConditionalSubscriptionIntent.CREATED)
          .withUpdateIntents(ConditionalSubscriptionIntent.MIGRATED)
          .withRemoveIntents(ConditionalSubscriptionIntent.DELETED)
          .withWaitStateType(WaitStateType.CONDITION);

  private WaitStateConfigs() {}
}
