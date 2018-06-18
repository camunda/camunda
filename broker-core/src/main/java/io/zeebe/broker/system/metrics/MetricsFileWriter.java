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
package io.zeebe.broker.system.metrics;

import io.zeebe.broker.Loggers;
import io.zeebe.util.FileUtil;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorPriority;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.time.Duration;
import org.agrona.ExpandableDirectByteBuffer;
import org.slf4j.Logger;

public class MetricsFileWriter extends Actor {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final MetricsManager metricsManager;
  private final Duration reportingInterval;
  private final String filePath;
  private final ExpandableDirectByteBuffer writeBuffer = new ExpandableDirectByteBuffer();
  private FileChannel fileChannel = null;

  public MetricsFileWriter(
      Duration reportingInterval, String filePath, MetricsManager metricsManager) {
    this.reportingInterval = reportingInterval;
    this.filePath = filePath;
    this.metricsManager = metricsManager;
  }

  @Override
  public String getName() {
    return "metricsFileWriter";
  }

  @Override
  protected void onActorStarting() {
    LOG.debug(
        "Writing metrics to file {}. Reporting interval {}s.",
        filePath,
        reportingInterval.toMillis() / 1000);
    fileChannel = FileUtil.openChannel(filePath, true);

    actor.setPriority(ActorPriority.LOW);
  }

  @Override
  protected void onActorStarted() {
    actor.runAtFixedRate(reportingInterval, this::dump);
  }

  private void dump() {
    final ActorClock clock = ActorClock.current();
    clock.update();

    final int length = metricsManager.dump(writeBuffer, 0, clock.getTimeMillis());
    final ByteBuffer inBuffer = writeBuffer.byteBuffer();
    inBuffer.position(0);
    inBuffer.limit(length);

    try {
      fileChannel.position(0);
      fileChannel.truncate(length);

      while (inBuffer.hasRemaining()) {
        fileChannel.write(inBuffer);
      }

      fileChannel.force(false);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  protected void onActorClosing() {
    FileUtil.closeSilently(fileChannel);
  }

  public ActorFuture<Void> close() {
    return actor.close();
  }
}
