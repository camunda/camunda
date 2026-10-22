/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

/** Configuration of the system partition's failure detector, which is opt-in. */
public final class FailureDetectorCfg implements ConfigurationEntry {
  private FailureDetectorType type = FailureDetectorType.NONE;
  private SwimCfg swim = new SwimCfg();

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    if (type == FailureDetectorType.SWIM) {
      swim.init(globalConfig, brokerBase);
    }
  }

  public FailureDetectorType getType() {
    return type;
  }

  public void setType(final FailureDetectorType type) {
    this.type = type;
  }

  public SwimCfg getSwim() {
    return swim;
  }

  public void setSwim(final SwimCfg swim) {
    this.swim = swim;
  }

  @Override
  public String toString() {
    return "FailureDetectorCfg{" + "type=" + type + ", swim=" + swim + '}';
  }
}
