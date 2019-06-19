/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import java.time.Duration;

public class AsyncSnapshotingDirectorService implements Service<AsyncSnapshotingDirectorService> {
  private final Injector<StreamProcessor> streamProcessorInjector = new Injector<>();

  private final LogStream logStream;
  private final StateSnapshotController snapshotController;
  private final Duration snapshotPeriod;
  private AsyncSnapshotDirector asyncSnapshotDirector;

  public AsyncSnapshotingDirectorService(
      final LogStream logStream,
      final StateSnapshotController snapshotController,
      Duration snapshotPeriod) {
    this.logStream = logStream;
    this.snapshotController = snapshotController;
    this.snapshotPeriod = snapshotPeriod;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    final StreamProcessor streamProcessor = streamProcessorInjector.getValue();

    asyncSnapshotDirector =
        new AsyncSnapshotDirector(streamProcessor, snapshotController, logStream, snapshotPeriod);

    startContext.getScheduler().submitActor(asyncSnapshotDirector);
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    if (asyncSnapshotDirector != null) {
      stopContext.async(asyncSnapshotDirector.closeAsync());
      asyncSnapshotDirector = null;
    }
  }

  @Override
  public AsyncSnapshotingDirectorService get() {
    return this;
  }

  public Injector<StreamProcessor> getStreamProcessorInjector() {
    return streamProcessorInjector;
  }
}
