/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.broker.system.configuration.backpressure.LimitCfg;
import io.camunda.zeebe.broker.system.configuration.backpressure.LimitCfg.LimitAlgorithm;

public class FlowControlCfg implements ConfigurationEntry {

  private static final ObjectMapper mapper =
      JsonMapper.builder().addModule(new JavaTimeModule()).build();
  private LimitCfg append = new LimitCfg();
  private LimitCfg request = null;

  public FlowControlCfg() {
    append.setAlgorithm(LimitAlgorithm.LEGACY_VEGAS);
    append.setUseWindowed(false);
  }

  public LimitCfg getAppend() {
    return append;
  }

  public void setAppend(final LimitCfg append) {
    this.append = append;
  }

  public LimitCfg getRequest() {
    return request;
  }

  public void setRequest(final LimitCfg request) {
    this.request = request;
  }

  public static FlowControlCfg deserialize(final String serialized) throws JsonProcessingException {
    return mapper.readValue(serialized, FlowControlCfg.class);
  }

  public byte[] serialize() throws JsonProcessingException {
    return mapper.writeValueAsBytes(this);
  }
}
