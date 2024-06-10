/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.service;

import io.camunda.tasklist.management.ILMPolicyUpdate;
import io.camunda.tasklist.property.TasklistProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

@Service
@DependsOn("tasklistSchemaStartup")
public class ILMService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ILMService.class);

  @Autowired private ILMPolicyUpdate ilmPolicyUpdate;

  @Autowired private TasklistProperties tasklistProperties;

  @PostConstruct
  public void init() throws IOException {
    if (tasklistProperties.isArchiverEnabled()) {
      if (tasklistProperties.getArchiver().isIlmEnabled()) {
        applyIlmPolicyToAllIndices();
      } else {
        removeIlmPolicyFromAllIndices();
      }
    } else {
      LOGGER.info("Archiver is not enabled, skipping ILM policy update");
    }
  }

  private void applyIlmPolicyToAllIndices() throws IOException {
    ilmPolicyUpdate.applyIlmPolicyToAllIndices();
  }

  private void removeIlmPolicyFromAllIndices() throws IOException {
    ilmPolicyUpdate.removeIlmPolicyFromAllIndices();
  }
}
