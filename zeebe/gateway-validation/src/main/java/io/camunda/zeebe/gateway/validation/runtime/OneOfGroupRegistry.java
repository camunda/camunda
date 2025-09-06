/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.validation.runtime;

import io.camunda.zeebe.gateway.validation.model.GroupDescriptor;
import io.camunda.zeebe.gateway.validation.spi.GroupDescriptorProvider;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/** Registry facade discovering providers via ServiceLoader (currently none). */
public final class OneOfGroupRegistry {
  public static final OneOfGroupRegistry INSTANCE = new OneOfGroupRegistry();

  private final ConcurrentHashMap<String, GroupDescriptor> cache = new ConcurrentHashMap<>();
  private final ServiceLoader<GroupDescriptorProvider> loader =
      ServiceLoader.load(GroupDescriptorProvider.class);

  private OneOfGroupRegistry() {}

  public GroupDescriptor get(final String groupId) {
    return cache.computeIfAbsent(groupId, this::load);
  }

  private GroupDescriptor load(final String id) {
    for (final GroupDescriptorProvider p : loader) {
      final GroupDescriptor d = p.find(id);
      if (d != null) {
        return d;
      }
    }
    return null; // not found
  }
}
