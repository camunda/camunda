/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.service;

import io.camunda.client.ZeebeClient;
import io.camunda.search.entities.GroupEntity;
import java.util.Collections;
import org.springframework.stereotype.Service;

@Service
public class GroupService {
  private final ZeebeClient zeebeClient;

  public GroupService(final ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  public GroupEntity create(final String name) {
    final var groupResponse = zeebeClient.newCreateGroupCommand().name(name).send().join();
    return new GroupEntity(groupResponse.getGroupKey(), name, Collections.emptySet());
  }
}
