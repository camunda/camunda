/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker;

import io.zeebe.broker.clustering.ClusterComponent;
import io.zeebe.broker.engine.EngineComponent;
import io.zeebe.broker.exporter.ExporterComponent;
import io.zeebe.broker.system.SystemComponent;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.transport.TransportComponent;
import io.zeebe.util.LogUtil;
import io.zeebe.util.sched.clock.ActorClock;
import java.io.InputStream;
import org.slf4j.Logger;

public class Broker implements AutoCloseable {
  public static final Logger LOG = Loggers.SYSTEM_LOGGER;

  public static final String VERSION;

  static {
    final String version = Broker.class.getPackage().getImplementationVersion();
    VERSION = version != null ? version : "development";
  }

  protected final SystemContext brokerContext;
  protected boolean isClosed = false;

  public Broker(final String configFileLocation, final String basePath, final ActorClock clock) {
    this(new SystemContext(configFileLocation, basePath, clock));
  }

  public Broker(final InputStream configStream, final String basePath, final ActorClock clock) {
    this(new SystemContext(configStream, basePath, clock));
  }

  public Broker(final BrokerCfg cfg, final String basePath, final ActorClock clock) {
    this(new SystemContext(cfg, basePath, clock));
  }

  public Broker(final SystemContext systemContext) {
    this.brokerContext = systemContext;
    LogUtil.doWithMDC(systemContext.getDiagnosticContext(), () -> start());
  }

  protected void start() {
    LOG.info("Version: {}", VERSION);
    LOG.info("Starting broker with configuration {}", getConfig().toJson());

    brokerContext.addComponent(new SystemComponent());
    brokerContext.addComponent(new TransportComponent());
    brokerContext.addComponent(new EngineComponent());
    brokerContext.addComponent(new ClusterComponent());
    brokerContext.addComponent(new ExporterComponent());

    brokerContext.init();
  }

  @Override
  public void close() {
    LogUtil.doWithMDC(
        brokerContext.getDiagnosticContext(),
        () -> {
          if (!isClosed) {
            brokerContext.close();
            isClosed = true;
            LOG.info("Broker shut down.");
          }
        });
  }

  public SystemContext getBrokerContext() {
    return brokerContext;
  }

  public BrokerCfg getConfig() {
    return brokerContext.getBrokerConfiguration();
  }
}
