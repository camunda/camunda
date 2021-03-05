/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.exporter.util;

import io.zeebe.exporter.api.Exporter;
import io.zeebe.protocol.record.Record;

/** Simple exported meant to be embedded into a JAR for testing */
public final class TestJarExporter implements Exporter {
  public static final String FOO = "bar";

  @Override
  public void export(final Record<?> record) {}
}
