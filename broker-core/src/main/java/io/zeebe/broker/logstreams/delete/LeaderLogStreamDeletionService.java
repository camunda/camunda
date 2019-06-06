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
package io.zeebe.broker.logstreams.delete;

import io.zeebe.broker.exporter.ExporterManagerService;
import io.zeebe.logstreams.impl.delete.DeletionService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class LeaderLogStreamDeletionService implements DeletionService, Service {
  private final Injector<ExporterManagerService> exporterManagerInjector = new Injector<>();
  private final LogStream logStream;
  private ExporterManagerService exporterManagerService;

  public LeaderLogStreamDeletionService(LogStream logStream) {
    this.logStream = logStream;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    exporterManagerService = exporterManagerInjector.getValue();
  }

  @Override
  public void delete(final long position) {
    final long minPosition = Math.min(position, getMinimumExportedPosition());
    logStream.delete(minPosition);
  }

  private long getMinimumExportedPosition() {
    return exporterManagerService.getLowestExporterPosition();
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {}

  @Override
  public LeaderLogStreamDeletionService get() {
    return this;
  }

  public Injector<ExporterManagerService> getExporterManagerInjector() {
    return exporterManagerInjector;
  }
}
