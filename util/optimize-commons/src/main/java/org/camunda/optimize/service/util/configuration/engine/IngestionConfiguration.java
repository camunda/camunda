/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;

import java.security.SecureRandom;
import java.util.Optional;

@Data
@Slf4j
@FieldNameConstants(asEnum = true)
public class IngestionConfiguration {
  private String accessToken;
  private long maxBatchRequestBytes;
  private int maxRequests;

  @JsonCreator
  public IngestionConfiguration(@JsonProperty("accessToken") final String accessToken,
                                @JsonProperty("maxBatchRequestLength") final long maxBatchRequestBytes,
                                @JsonProperty("maxRequests") final int maxRequests) {
    this.accessToken = Optional.ofNullable(accessToken).orElseGet(() -> {
      final String generatedSecret = RandomStringUtils.random(
        16, 0, 0, true, true, null, new SecureRandom()
      );
      log.info("No ingestion api secret was configured, generated a temporary token [{}].", generatedSecret);
      return generatedSecret;
    });
    this.maxBatchRequestBytes = maxBatchRequestBytes;
    this.maxRequests = maxRequests;
  }

}
