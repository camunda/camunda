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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;

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
          .withRemoveIntents(JobIntent.COMPLETED, JobIntent.CANCELED)
          .withSupportedElementTypes(
              BpmnElementType.SERVICE_TASK,
              BpmnElementType.SCRIPT_TASK,
              BpmnElementType.SEND_TASK,
              BpmnElementType.BUSINESS_RULE_TASK)
          .withWaitStateType(WaitStateType.JOB);

  private WaitStateConfigs() {}
}
