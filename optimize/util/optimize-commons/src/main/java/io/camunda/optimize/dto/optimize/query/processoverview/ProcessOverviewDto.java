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
    final int PRIME = 59;
    int result = 1;
    final Object $owner = getOwner();
    result = result * PRIME + ($owner == null ? 43 : $owner.hashCode());
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $digest = getDigest();
    result = result * PRIME + ($digest == null ? 43 : $digest.hashCode());
    final Object $lastKpiEvaluationResults = getLastKpiEvaluationResults();
    result =
        result * PRIME
            + ($lastKpiEvaluationResults == null ? 43 : $lastKpiEvaluationResults.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessOverviewDto)) {
      return false;
    }
    final ProcessOverviewDto other = (ProcessOverviewDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$owner = getOwner();
    final Object other$owner = other.getOwner();
    if (this$owner == null ? other$owner != null : !this$owner.equals(other$owner)) {
      return false;
    }
    final Object this$processDefinitionKey = getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$digest = getDigest();
    final Object other$digest = other.getDigest();
    if (this$digest == null ? other$digest != null : !this$digest.equals(other$digest)) {
      return false;
    }
    final Object this$lastKpiEvaluationResults = getLastKpiEvaluationResults();
    final Object other$lastKpiEvaluationResults = other.getLastKpiEvaluationResults();
    if (this$lastKpiEvaluationResults == null
        ? other$lastKpiEvaluationResults != null
        : !this$lastKpiEvaluationResults.equals(other$lastKpiEvaluationResults)) {
      return false;
    }
    return true;
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

  public static final class Fields {

    public static final String owner = "owner";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String digest = "digest";
    public static final String lastKpiEvaluationResults = "lastKpiEvaluationResults";
  }
}
