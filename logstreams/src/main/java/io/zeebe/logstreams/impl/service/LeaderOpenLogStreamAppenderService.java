/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.service;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class LeaderOpenLogStreamAppenderService implements Service<Void> {
  private final Injector<LogStream> logStreamInjector = new Injector<>();
  private LogStream logStream;

  @Override
  public void start(ServiceStartContext startContext) {
    logStream = logStreamInjector.getValue();
    startContext.async(logStream.openAppender());
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(logStream.closeAppender());
  }

  @Override
  public Void get() {
    return null;
  }

  public Injector<LogStream> getLogStreamInjector() {
    return logStreamInjector;
  }
}
