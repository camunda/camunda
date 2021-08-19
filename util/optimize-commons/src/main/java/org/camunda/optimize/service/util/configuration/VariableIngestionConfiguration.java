/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;

import java.security.SecureRandom;
import java.util.Optional;

@Slf4j
@Data
public class VariableIngestionConfiguration {
  private String accessToken;

  @JsonCreator
  public VariableIngestionConfiguration(@JsonProperty("accessToken") final String accessToken) {
    this.accessToken = Optional.ofNullable(accessToken).orElseGet(() -> {
      final String generatedSecret = RandomStringUtils.random(
        16, 0, 0, true, true, null, new SecureRandom()
      );
      log.info("No variable ingestion api secret was configured, generated a temporary token [{}].", generatedSecret);
      return generatedSecret;
    });
  }
}
