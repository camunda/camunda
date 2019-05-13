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
package io.zeebe.engine.util;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.test.util.TestUtil;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.junit.rules.ExternalResource;

public class LogStreamWriterRule extends ExternalResource {
  private final LogStreamRule logStreamRule;

  private LogStream logStream;
  private LogStreamRecordWriter logStreamWriter;

  public LogStreamWriterRule(final LogStreamRule logStreamRule) {
    this.logStreamRule = logStreamRule;
  }

  @Override
  protected void before() {
    this.logStream = logStreamRule.getLogStream();
    this.logStreamWriter = new LogStreamWriterImpl(logStream);
  }

  @Override
  protected void after() {
    logStreamWriter = null;
    logStream = null;
  }

  public void wrap(LogStreamRule rule) {
    this.logStream = rule.getLogStream();
    this.logStreamWriter.wrap(logStream);
  }

  public long writeEvents(final int count, final DirectBuffer event) {
    long lastPosition = -1;
    for (int i = 1; i <= count; i++) {
      final long key = i;
      lastPosition = writeEventInternal(w -> w.key(key).value(event));
    }

    waitForPositionToBeAppended(lastPosition);

    return lastPosition;
  }

  public long writeEvent(final DirectBuffer event) {
    return writeEvent(w -> w.value(event));
  }

  public long writeEvent(final Consumer<LogStreamRecordWriter> writer) {
    final long position = writeEventInternal(writer);

    waitForPositionToBeAppended(position);

    return position;
  }

  private long writeEventInternal(final Consumer<LogStreamRecordWriter> writer) {
    long position;
    do {
      position = tryWrite(writer);
    } while (position == -1);

    return position;
  }

  public long tryWrite(final DirectBuffer value) {
    return tryWrite(w -> w.keyNull().value(value));
  }

  public long tryWrite(final Consumer<LogStreamRecordWriter> writer) {
    writer.accept(logStreamWriter);

    return logStreamWriter.tryWrite();
  }

  public void waitForPositionToBeAppended(final long position) {
    TestUtil.waitUntil(
        () -> logStream.getCommitPosition() >= position, // Now only committed events are appended.
        "Failed to wait for position {} to be appended",
        position);
  }
}
