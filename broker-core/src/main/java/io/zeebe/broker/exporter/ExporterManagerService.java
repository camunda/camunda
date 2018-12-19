/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.exporter;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.exporter.jar.ExporterJarLoadException;
import io.zeebe.broker.exporter.repo.ExporterLoadException;
import io.zeebe.broker.exporter.repo.ExporterRepository;
import io.zeebe.broker.exporter.stream.ExporterColumnFamilies;
import io.zeebe.broker.exporter.stream.ExporterStreamProcessor;
import io.zeebe.broker.logstreams.processor.StreamProcessorServiceFactory;
import io.zeebe.broker.logstreams.state.DefaultZeebeDbFactory;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import java.util.List;

public class ExporterManagerService implements Service<ExporterManagerService> {
  public static final int EXPORTER_PROCESSOR_ID = 1003;
  public static final String PROCESSOR_NAME = "exporter";

  private final Injector<StreamProcessorServiceFactory> streamProcessorServiceFactoryInjector =
      new Injector<>();

  private final ServiceGroupReference<Partition> partitionsGroupReference =
      ServiceGroupReference.<Partition>create().onAdd(this::startExporter).build();

  private final List<ExporterCfg> exporterCfgs;
  private final ExporterRepository exporterRepository;

  private StreamProcessorServiceFactory streamProcessorServiceFactory;

  public ExporterManagerService(List<ExporterCfg> exporterCfgs) {
    this.exporterCfgs = exporterCfgs;
    this.exporterRepository = new ExporterRepository();
  }

  @Override
  public ExporterManagerService get() {
    return this;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    this.streamProcessorServiceFactory = streamProcessorServiceFactoryInjector.getValue();
    // load and validate exporters
    for (ExporterCfg exporterCfg : exporterCfgs) {
      try {
        exporterRepository.load(exporterCfg);
      } catch (ExporterLoadException | ExporterJarLoadException e) {
        throw new RuntimeException("Failed to load exporter with configuration: " + exporterCfg, e);
      }
    }
  }

  private void startExporter(ServiceName<Partition> partitionName, Partition partition) {
    final StateStorage stateStorage =
        partition.getStateStorageFactory().create(EXPORTER_PROCESSOR_ID, PROCESSOR_NAME);

    final SnapshotController snapshotController =
        new StateSnapshotController(
            DefaultZeebeDbFactory.defaultFactory(ExporterColumnFamilies.class), stateStorage);

    streamProcessorServiceFactory
        .createService(partition, partitionName)
        .processorId(EXPORTER_PROCESSOR_ID)
        .processorName(PROCESSOR_NAME)
        .snapshotController(snapshotController)
        .streamProcessorFactory(
            (zeebeDb) ->
                new ExporterStreamProcessor(
                    zeebeDb,
                    partition.getInfo().getPartitionId(),
                    exporterRepository.getExporters().values()))
        .build();
  }

  public Injector<StreamProcessorServiceFactory> getStreamProcessorServiceFactoryInjector() {
    return streamProcessorServiceFactoryInjector;
  }

  public ServiceGroupReference<Partition> getPartitionsGroupReference() {
    return partitionsGroupReference;
  }
}
