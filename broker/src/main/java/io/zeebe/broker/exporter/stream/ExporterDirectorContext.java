/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter.stream;

import io.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.zeebe.db.ZeebeDb;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import java.time.Duration;
import java.util.Collection;

public class ExporterDirectorContext {

  private int id;
  private String name;

  private LogStream logStream;
  private LogStreamReader logStreamReader;

  private Collection<ExporterDescriptor> descriptors;

  private Duration snapshotPeriod;
  private ZeebeDb zeebeDb;
  private int maxSnapshots;

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public LogStreamReader getLogStreamReader() {
    return logStreamReader;
  }

  public Collection<ExporterDescriptor> getDescriptors() {
    return descriptors;
  }

  public Duration getSnapshotPeriod() {
    return snapshotPeriod;
  }

  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }

  public int getMaxSnapshots() {
    return maxSnapshots;
  }

  public ExporterDirectorContext id(int id) {
    this.id = id;
    return this;
  }

  public ExporterDirectorContext name(String name) {
    this.name = name;
    return this;
  }

  public ExporterDirectorContext logStream(LogStream logStream) {
    this.logStream = logStream;
    return this;
  }

  public ExporterDirectorContext logStreamReader(LogStreamReader logStreamReader) {
    this.logStreamReader = logStreamReader;
    return this;
  }

  public ExporterDirectorContext descriptors(Collection<ExporterDescriptor> descriptors) {
    this.descriptors = descriptors;
    return this;
  }

  public ExporterDirectorContext snapshotPeriod(Duration snapshotPeriod) {
    this.snapshotPeriod = snapshotPeriod;
    return this;
  }

  public ExporterDirectorContext zeebeDb(ZeebeDb zeebeDb) {
    this.zeebeDb = zeebeDb;
    return this;
  }

  public ExporterDirectorContext maxSnapshots(int maxSnapshots) {
    this.maxSnapshots = maxSnapshots;
    return this;
  }
}
