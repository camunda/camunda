/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Column families whose reads and scans start at one end of the key space, so a store that keeps
 * only a window of keys at that end in memory (the write-behind key index) serves them although
 * they hold more keys than the window.
 *
 * <p>{@link #READ_FROM_LOWEST_KEY} is scanned from the lowest key (next due entry). This includes
 * {@code JOB_ACTIVATABLE_BY_PRIORITY}, a queue keyed by job type, inverted priority and job key:
 * job activation scans a type from its front and every activated job leaves a delete there, so
 * without the window each scan steps over the deletes of all earlier jobs. With several job types
 * the window covers the front of the lowest types only.
 *
 * <p>{@link #READ_FROM_HIGHEST_KEY} has keys that start with an element instance, scope, job or
 * process instance key, which grows with time, so the engine reads and scans the newest keys. Most
 * of their lookups and scans find nothing: the engine checks for variables, event triggers, timers
 * and subscriptions when an element ends. With one tenant the same holds for {@code
 * COMPENSATION_SUBSCRIPTION}, whose keys start with the tenant. {@code
 * PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY} is left out, because the definition key comes first.
 */
@NullMarked
public final class ColumnFamilyAccessPatterns {

  public static final Set<ZbColumnFamilies> READ_FROM_LOWEST_KEY =
      Collections.unmodifiableSet(
          EnumSet.of(
              ZbColumnFamilies.TIMER_DUE_DATES,
              ZbColumnFamilies.JOB_DEADLINES,
              ZbColumnFamilies.MESSAGE_DEADLINES,
              ZbColumnFamilies.JOB_BACKOFF,
              ZbColumnFamilies.JOB_ACTIVATABLE_BY_PRIORITY));

  public static final Set<ZbColumnFamilies> READ_FROM_HIGHEST_KEY =
      Collections.unmodifiableSet(
          EnumSet.of(
              ZbColumnFamilies.ELEMENT_INSTANCE_PARENT_CHILD,
              ZbColumnFamilies.NUMBER_OF_TAKEN_SEQUENCE_FLOWS,
              ZbColumnFamilies.ELEMENT_INSTANCE_KEY,
              ZbColumnFamilies.ELEMENT_INSTANCE_CHILD_PARENT,
              ZbColumnFamilies.EVENT_SCOPE,
              ZbColumnFamilies.EVENT_TRIGGER,
              ZbColumnFamilies.VARIABLES,
              ZbColumnFamilies.TIMERS,
              ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_KEY,
              ZbColumnFamilies.SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME,
              ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_BY_SCOPE_KEY,
              ZbColumnFamilies.SECRET_REFERENCES_BY_JOB,
              ZbColumnFamilies.BANNED_INSTANCE,
              ZbColumnFamilies.SUSPENDED_PROCESS_INSTANCES,
              ZbColumnFamilies.JOBS,
              ZbColumnFamilies.JOB_STATES,
              ZbColumnFamilies.RUNTIME_INSTRUCTIONS,
              ZbColumnFamilies.AWAIT_WORKLOW_RESULT,
              ZbColumnFamilies.COMPENSATION_SUBSCRIPTION));

  private ColumnFamilyAccessPatterns() {}
}
