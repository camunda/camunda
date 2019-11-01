/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.exporter.repo.ExporterRepository;
import io.zeebe.broker.exporter.stream.ExporterDirector;
import io.zeebe.broker.exporter.stream.ExporterDirectorContext;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.db.ZeebeDb;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.DurationUtil;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

public class ExporterDirectorService implements Service<ExporterDirectorService> {

  public static final int EXPORTER_PROCESSOR_ID = 1003;
  public static final String EXPORTER_NAME = "exporter-%d";

  private final ExporterRepository exporterRepository;
  private final DataCfg dataCfg;
  private ServiceStartContext startContext;
  private ExporterDirector director;
  private final Injector<Partition> partitionInjector = new Injector<>();

  public ExporterDirectorService(BrokerCfg brokerCfg, ExporterRepository exporterRepository) {
    this.dataCfg = brokerCfg.getData();
    this.exporterRepository = exporterRepository;
  }

  public Injector<Partition> getPartitionInjector() {
    return partitionInjector;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    this.startContext = startContext;
    startExporter(partitionInjector.getValue(), startContext);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(director.stopAsync());
  }

  @Override
  public ExporterDirectorService get() {
    return this;
  }

  private void startExporter(Partition partition, ServiceStartContext startContext) {
    final ZeebeDb zeebeDb = partition.getZeebeDb();

    if (exporterRepository.getExporters().isEmpty()) {
      final ExporterClearStateService clearStateService =
          new ExporterClearStateService(partition.getZeebeDb());
      clearStateService.start();
    } else {
      final ExporterDirectorContext context =
          new ExporterDirectorContext()
              .id(EXPORTER_PROCESSOR_ID)
              .name(String.format(EXPORTER_NAME, partition.getPartitionId()))
              .logStream(partition.getLogStream())
              .zeebeDb(zeebeDb)
              .maxSnapshots(dataCfg.getMaxSnapshots())
              .descriptors(exporterRepository.getExporters().values())
              .logStreamReader(new BufferedLogStreamReader())
              .snapshotPeriod(DurationUtil.parse(dataCfg.getSnapshotPeriod()));

      director = new ExporterDirector(context);
      startContext.async(director.startAsync(this.startContext.getScheduler()), true);
    }
  }

  public ActorFuture<Long> getLowestExporterPosition() {
    if (exporterRepository.getExporters().isEmpty()) {
      return CompletableActorFuture.completed(Long.MAX_VALUE);
    } else {
      return director.getLowestExporterPosition();
    }
  }
}
