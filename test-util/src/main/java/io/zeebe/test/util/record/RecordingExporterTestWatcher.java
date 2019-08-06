/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.record;

import io.zeebe.util.ZbLogger;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;

public class RecordingExporterTestWatcher extends TestWatcher {

  public static final Logger LOG = new ZbLogger("io.zeebe.test.records");

  @Override
  protected void failed(Throwable e, Description description) {
    LOG.info("Test failed, following records where exported:");
    RecordingExporter.getRecords().forEach(r -> LOG.info(r.toString()));
  }

  @Override
  protected void starting(Description description) {
    RecordingExporter.reset();
  }
}
