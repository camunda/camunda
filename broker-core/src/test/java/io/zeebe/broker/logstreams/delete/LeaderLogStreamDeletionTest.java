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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.broker.engine.EngineServiceNames;
import io.zeebe.broker.exporter.ExporterManagerService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.mockito.Mock;

public class LeaderLogStreamDeletionTest {
  private static final int PARTITION_ID = 0;
  private final ActorSchedulerRule actorScheduler = new ActorSchedulerRule();
  private final ServiceContainerRule serviceContainer = new ServiceContainerRule(actorScheduler);

  @Rule public RuleChain chain = RuleChain.outerRule(actorScheduler).around(serviceContainer);

  @Mock ExporterManagerService mockExporterManagerService;
  @Mock LogStream mockLogStream = mock(LogStream.class);

  private LeaderLogStreamDeletionService deletionService;

  private static final long POSITION_TO_DELETE = 6L;
  private static final long ADDRESS_TO_DELETE = 55L;

  @Before
  public void setup() {
    deletionService = new LeaderLogStreamDeletionService(mockLogStream);
    serviceContainer
        .get()
        .createService(
            EngineServiceNames.leaderLogStreamDeletionService(PARTITION_ID), deletionService)
        .install()
        .join();
    final Injector<ExporterManagerService> exporterManagerInjector =
        deletionService.getExporterManagerInjector();

    mockExporterManagerService = mock(ExporterManagerService.class);
    exporterManagerInjector.inject(mockExporterManagerService);
    deletionService.start(mock(ServiceStartContext.class));
  }

  @Test
  public void shouldDeleteWithDelayedExporter() {
    // given
    when(mockExporterManagerService.getLowestExporterPosition()).thenReturn(POSITION_TO_DELETE);

    // when
    deletionService.delete(POSITION_TO_DELETE + 2);

    // then
    verify(mockLogStream, times(1)).delete(POSITION_TO_DELETE);
  }

  @Test
  public void shouldDeleteWithNoExporter() {
    // given
    when(mockExporterManagerService.getLowestExporterPosition()).thenReturn(Long.MAX_VALUE);

    // when
    deletionService.delete(POSITION_TO_DELETE);

    // then
    verify(mockLogStream, times(1)).delete(POSITION_TO_DELETE);
  }

  @Test
  public void shouldNotDeleteOnNegativePosition() {
    // given
    when(mockExporterManagerService.getLowestExporterPosition()).thenReturn(Long.MAX_VALUE);

    // when
    deletionService.delete(-1);

    // then
    verify(mockLogStream, never()).delete(POSITION_TO_DELETE);
  }
}
