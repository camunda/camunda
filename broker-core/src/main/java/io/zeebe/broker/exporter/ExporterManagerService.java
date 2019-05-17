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

import static io.zeebe.broker.exporter.ExporterServiceNames.exporterDirectorServiceName;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.exporter.jar.ExporterJarLoadException;
import io.zeebe.broker.exporter.repo.ExporterLoadException;
import io.zeebe.broker.exporter.repo.ExporterRepository;
import io.zeebe.broker.exporter.stream.ExporterColumnFamilies;
import io.zeebe.broker.exporter.stream.ExporterDirector;
import io.zeebe.broker.exporter.stream.ExporterDirectorContext;
import io.zeebe.broker.exporter.stream.ExportersState;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.db.ZeebeDb;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.util.DurationUtil;
import java.util.List;
import org.slf4j.Logger;

public class ExporterManagerService implements Service<ExporterManagerService> {

  public static final int EXPORTER_PROCESSOR_ID = 1003;
  public static final String PROCESSOR_NAME = "exporter";

  private static final Logger LOG = Loggers.EXPORTER_LOGGER;

  private final ServiceGroupReference<Partition> partitionsGroupReference =
      ServiceGroupReference.<Partition>create().onAdd(this::startExporter).build();

  private final List<ExporterCfg> exporterCfgs;
  private final ExporterRepository exporterRepository;
  private final DataCfg dataCfg;

  private ServiceStartContext startContext;

  public ExporterManagerService(BrokerCfg brokerCfg) {
    this.dataCfg = brokerCfg.getData();
    this.exporterCfgs = brokerCfg.getExporters();
    this.exporterRepository = new ExporterRepository();
  }

  @Override
  public ExporterManagerService get() {
    return this;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    this.startContext = startContext;
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
    final SnapshotController snapshotController = partition.getExporterSnapshotController();

    if (exporterRepository.getExporters().isEmpty()) {
      clearExporterState(snapshotController);
      partition.getLogStream().setExporterPositionSupplier(() -> Long.MAX_VALUE);
    } else {
      final ExporterDirectorContext context =
          new ExporterDirectorContext()
              .eventFilter((event -> true))
              .id(EXPORTER_PROCESSOR_ID)
              .name(PROCESSOR_NAME)
              .logStream(partition.getLogStream())
              .snapshotController(snapshotController)
              .maxSnapshots(dataCfg.getMaxSnapshots())
              .descriptors(exporterRepository.getExporters().values())
              .logStreamReader(new BufferedLogStreamReader())
              .snapshotPeriod(DurationUtil.parse(dataCfg.getSnapshotPeriod()));

      final LogStream logStream = partition.getLogStream();
      final String logName = logStream.getLogName();

      final ExporterDirector director = new ExporterDirector(context);
      startContext
          .createService(exporterDirectorServiceName(partition.getPartitionId()), director)
          .dependency(LogStreamServiceNames.logStreamServiceName(logName))
          .dependency(LogStreamServiceNames.logWriteBufferServiceName(logName))
          .dependency(LogStreamServiceNames.logStorageServiceName(logName))
          .dependency(LogStreamServiceNames.logBlockIndexServiceName(logName))
          .dependency(partitionName)
          .install();
      logStream.setExporterPositionSupplier(director::getLowestExporterPosition);
    }
  }

  private void clearExporterState(SnapshotController snapshotController) {
    // We need to remove the exporter positions from the state in case that one of the exporters is
    // configured later again. The processor would try to continue from the previous position which
    // may not
    // exist anymore in the logstream.

    try {
      // TODO (saig0): don't open and recover the latest snapshot in the service - #2353
      final long snapshotPosition = snapshotController.recover();
      final ZeebeDb<ExporterColumnFamilies> db = snapshotController.openDb();
      final ExportersState state = new ExportersState(db, db.createContext());

      state.visitPositions(
          (exporterId, position) -> {
            state.removePosition(exporterId);

            LOG.info(
                "The exporter '{}' is not configured anymore. Its position is removed from the state.",
                exporterId);
          });

      // TODO (saig0): don't take a new snapshot in the service - #2353
      snapshotController.takeSnapshot(snapshotPosition + 1);

    } catch (Exception e) {
      LOG.error("Failed to remove exporters from state", e);
    } finally {
      try {
        snapshotController.close();
      } catch (Exception e) {
        LOG.error("Unexpected exception happened on closing snapshot controller.", e);
      }
    }
  }

  public ServiceGroupReference<Partition> getPartitionsGroupReference() {
    return partitionsGroupReference;
  }
}
