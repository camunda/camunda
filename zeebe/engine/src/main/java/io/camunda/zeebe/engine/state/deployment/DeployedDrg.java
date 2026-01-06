/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import org.agrona.DirectBuffer;

/**
 * This class is a similar to the {@link DeployedProcess} class. It is a wrapper that contains both
 * the parsed DRG and the persisted DRG. This object is cached upon retrieving a DRG from the state.
 */
public final class DeployedDrg {
  private final ParsedDecisionRequirementsGraph parsedDecisionRequirements;

  private final PersistedDecisionRequirements persistedDecisionRequirements;

  public DeployedDrg(
      final ParsedDecisionRequirementsGraph parsedDecisionRequirements,
      final PersistedDecisionRequirements persistedDecisionRequirements) {
    this.parsedDecisionRequirements = parsedDecisionRequirements;
    this.persistedDecisionRequirements = persistedDecisionRequirements;
  }

  public ParsedDecisionRequirementsGraph getParsedDecisionRequirements() {
    return parsedDecisionRequirements;
  }

  public int getDecisionRequirementsVersion() {
    return persistedDecisionRequirements.getDecisionRequirementsVersion();
  }

  public DirectBuffer getResourceName() {
    return persistedDecisionRequirements.getResourceName();
  }

  public DirectBuffer getChecksum() {
    return persistedDecisionRequirements.getChecksum();
  }

  public long getDecisionRequirementsKey() {
    return persistedDecisionRequirements.getDecisionRequirementsKey();
  }

  public DirectBuffer getDecisionRequirementsId() {
    return persistedDecisionRequirements.getDecisionRequirementsId();
  }

  public DirectBuffer getDecisionRequirementsName() {
    return persistedDecisionRequirements.getDecisionRequirementsName();
  }

  public DirectBuffer getResource() {
    return persistedDecisionRequirements.getResource();
  }

  public String getTenantId() {
    return persistedDecisionRequirements.getTenantId();
  }

  public PersistedDecisionRequirements getPersistedDecisionRequirements() {
    return persistedDecisionRequirements;
  }

  public long getDeploymentKey() {
    return persistedDecisionRequirements.getDeploymentKey();
  }
}
