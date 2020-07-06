/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment.model.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class YamlTask {
  private String id = "";

  private String type = "";
  private String retries = ZeebeTaskDefinition.DEFAULT_RETRIES;

  private Map<String, String> headers = new HashMap<>();

  private List<YamlMapping> inputs = new ArrayList<>();
  private List<YamlMapping> outputs = new ArrayList<>();

  private boolean end = false;

  @JsonProperty("goto")
  private String next;

  @JsonProperty("switch")
  private List<YamlCase> cases = new ArrayList<>();

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getRetries() {
    return retries;
  }

  public void setRetries(final String retries) {
    this.retries = retries;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(final Map<String, String> headers) {
    this.headers = headers;
  }

  public List<YamlMapping> getInputs() {
    return inputs;
  }

  public void setInputs(final List<YamlMapping> inputs) {
    this.inputs = inputs;
  }

  public List<YamlMapping> getOutputs() {
    return outputs;
  }

  public void setOutputs(final List<YamlMapping> outputs) {
    this.outputs = outputs;
  }

  public List<YamlCase> getCases() {
    return cases;
  }

  public void setCases(final List<YamlCase> cases) {
    this.cases = cases;
  }

  public String getNext() {
    return next;
  }

  public void setNext(final String next) {
    this.next = next;
  }

  public boolean isEnd() {
    return end;
  }

  public void setEnd(final boolean end) {
    this.end = end;
  }
}
