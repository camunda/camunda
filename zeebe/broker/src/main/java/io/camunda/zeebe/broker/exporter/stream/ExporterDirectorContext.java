/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector.ExporterInitializationInfo;
import io.camunda.zeebe.broker.system.partitions.PartitionMessagingService;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.stream.api.EventFilter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Map;

public final class ExporterDirectorContext {

  public static final Duration DEFAULT_DISTRIBUTION_INTERVAL = Duration.ofSeconds(15);

  private int id;
  private String name;
  private LogStream logStream;
  private Map<ExporterDescriptor, ExporterInitializationInfo> descriptors;
  private ZeebeDb zeebeDb;
  private PartitionMessagingService partitionMessagingService;
  private ExporterMode exporterMode = ExporterMode.ACTIVE; // per default we export records
  private Duration distributionInterval = DEFAULT_DISTRIBUTION_INTERVAL;
  private EventFilter positionsToSkipFilter;
  private MeterRegistry meterRegistry;
  private InstantSource clock;
  private String engineName;
  private boolean sendOnLegacySubject = true;
  private boolean receiveOnLegacySubject = true;

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public Map<ExporterDescriptor, ExporterInitializationInfo> getDescriptors() {
    return descriptors;
  }

  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }

  public PartitionMessagingService getPartitionMessagingService() {
    return partitionMessagingService;
  }

  public ExporterMode getExporterMode() {
    return exporterMode;
  }

  public Duration getDistributionInterval() {
    return distributionInterval;
  }

  public EventFilter getPositionsToSkipFilter() {
    return positionsToSkipFilter;
  }

  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  public InstantSource getClock() {
    return clock;
  }

  public String getEngineName() {
    return engineName;
  }

  public boolean isSendOnLegacySubject() {
    return sendOnLegacySubject;
  }

  public boolean isReceiveOnLegacySubject() {
    return receiveOnLegacySubject;
  }

  public ExporterDirectorContext id(final int id) {
    this.id = id;
    return this;
  }

  public ExporterDirectorContext name(final String name) {
    this.name = name;
    return this;
  }

  public ExporterDirectorContext logStream(final LogStream logStream) {
    this.logStream = logStream;
    return this;
  }

  public ExporterDirectorContext descriptors(
      final Map<ExporterDescriptor, ExporterInitializationInfo> descriptors) {
    this.descriptors = descriptors;
    return this;
  }

  public ExporterDirectorContext zeebeDb(final ZeebeDb zeebeDb) {
    this.zeebeDb = zeebeDb;
    return this;
  }

  public ExporterDirectorContext meterRegistry(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    return this;
  }

  public ExporterDirectorContext partitionMessagingService(
      final PartitionMessagingService messagingService) {
    partitionMessagingService = messagingService;
    return this;
  }

  public ExporterDirectorContext exporterMode(final ExporterMode exporterMode) {
    this.exporterMode = exporterMode;
    return this;
  }

  public ExporterDirectorContext distributionInterval(final Duration distributionInterval) {
    this.distributionInterval = distributionInterval;
    return this;
  }

  public ExporterDirectorContext positionsToSkipFilter(final EventFilter skipPositionsFilter) {
    positionsToSkipFilter = skipPositionsFilter;
    return this;
  }

  public ExporterDirectorContext clock(final InstantSource clock) {
    this.clock = clock;
    return this;
  }

  public ExporterDirectorContext engineName(final String engineName) {
    this.engineName = engineName;
    return this;
  }

  public ExporterDirectorContext sendOnLegacySubject(final boolean sendOnLegacySubject) {
    this.sendOnLegacySubject = sendOnLegacySubject;
    return this;
  }

  public ExporterDirectorContext receiveOnLegacySubject(final boolean receiveOnLegacySubject) {
    this.receiveOnLegacySubject = receiveOnLegacySubject;
    return this;
  }

  public enum ExporterMode {
    /**
     * ACTIVE, means it is actively running the exporting and distributes the exporter positions to
     * the followers. This mode is used on the leader side.
     */
    ACTIVE,
    /**
     * PASSIVE, means it is not actively exporting records. It is consuming the distributed exporter
     * positions and stores them in the state. This mode is used on the follower side.
     */
    PASSIVE
  };
}
