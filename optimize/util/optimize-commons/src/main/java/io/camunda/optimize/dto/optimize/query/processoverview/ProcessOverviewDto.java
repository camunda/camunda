/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.processoverview;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.util.Map;

public class ProcessOverviewDto implements OptimizeDto {

  private String owner;
  private String processDefinitionKey;
  private ProcessDigestDto digest;
  private Map<String, String> lastKpiEvaluationResults;

  public ProcessOverviewDto(
      final String owner,
      final String processDefinitionKey,
      final ProcessDigestDto digest,
      final Map<String, String> lastKpiEvaluationResults) {
    this.owner = owner;
    this.processDefinitionKey = processDefinitionKey;
    this.digest = digest;
    this.lastKpiEvaluationResults = lastKpiEvaluationResults;
  }

  public ProcessOverviewDto() {}

  public String getOwner() {
    return owner;
  }

  public void setOwner(final String owner) {
    this.owner = owner;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public ProcessDigestDto getDigest() {
    return digest;
  }

  public void setDigest(final ProcessDigestDto digest) {
    this.digest = digest;
  }

  public Map<String, String> getLastKpiEvaluationResults() {
    return lastKpiEvaluationResults;
  }

  public void setLastKpiEvaluationResults(final Map<String, String> lastKpiEvaluationResults) {
    this.lastKpiEvaluationResults = lastKpiEvaluationResults;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessOverviewDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "ProcessOverviewDto(owner="
        + getOwner()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", digest="
        + getDigest()
        + ", lastKpiEvaluationResults="
        + getLastKpiEvaluationResults()
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String owner = "owner";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String digest = "digest";
    public static final String lastKpiEvaluationResults = "lastKpiEvaluationResults";
  }
}
