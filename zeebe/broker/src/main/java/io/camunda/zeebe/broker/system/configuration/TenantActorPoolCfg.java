/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import org.jspecify.annotations.NullMarked;

/**
 * The settings of the actor scheduler of each physical tenant.
 *
 * @param enabled whether each physical tenant gets a scheduler of its own
 * @param cpuThreads the CPU threads of the scheduler of each physical tenant
 * @param ioThreads the IO threads of the scheduler of each physical tenant
 */
@NullMarked
public record TenantActorPoolCfg(boolean enabled, int cpuThreads, int ioThreads) {
  public static final TenantActorPoolCfg DISABLED = new TenantActorPoolCfg(false, 2, 2);
}
