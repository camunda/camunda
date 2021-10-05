/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import java.io.IOException;

public interface PartitionAdminControl {
  StreamProcessor getStreamProcessor();

  ExporterDirector getExporterDirector();

  void triggerSnapshot();

  boolean shouldProcess();

  boolean shouldExport();

  void pauseProcessing() throws IOException;

  void resumeProcessing() throws IOException;

  boolean pauseExporting() throws IOException;

  boolean resumeExporting() throws IOException;
}
