/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import io.zeebe.servicecontainer.ServiceName;

public class StreamProcessorServiceNames {

  public static final ServiceName<StreamProcessor> streamProcessorService(String logName) {
    return ServiceName.newServiceName(
        String.format("logstream.%s.stream-processor", logName), StreamProcessor.class);
  }

  public static final ServiceName<AsyncSnapshotingDirectorService> asyncSnapshotingDirectorService(
      String logName) {
    return ServiceName.newServiceName(
        String.format("logstream.%s.snapshot-director", logName),
        AsyncSnapshotingDirectorService.class);
  }
}
