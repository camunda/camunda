/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.EngineConfiguration;

public final class EngineCfg implements ConfigurationEntry {

  private MessagesCfg messages = new MessagesCfg();
  private CachesCfg caches = new CachesCfg();

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    messages.init(globalConfig, brokerBase);
    caches.init(globalConfig, brokerBase);
  }

  public MessagesCfg getMessages() {
    return messages;
  }

  public void setMessages(final MessagesCfg messages) {
    this.messages = messages;
  }

  public CachesCfg getCaches() {
    return caches;
  }

  public void setCaches(final CachesCfg caches) {
    this.caches = caches;
  }

  @Override
  public String toString() {
    return "EngineCfg{" + "messages=" + messages + ", caches=" + caches + '}';
  }

  public EngineConfiguration createEngineConfiguration() {
    return new EngineConfiguration()
        .setMessagesTtlCheckerBatchLimit(messages.getTtlCheckerBatchLimit())
        .setMessagesTtlCheckerInterval(messages.getTtlCheckerInterval())
        .setDrgCacheCapacity(caches.getDrgCacheCapacity());
  }
}
