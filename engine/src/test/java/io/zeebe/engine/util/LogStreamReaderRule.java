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

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.rules.ExternalResource;

public class LogStreamReaderRule extends ExternalResource {

  private final LogStreamRule logStreamRule;
  private final LogStreamReader logStreamReader;

  public LogStreamReaderRule(final LogStreamRule logStreamRule) {
    this.logStreamRule = logStreamRule;
    logStreamReader = new BufferedLogStreamReader();
  }

  @Override
  protected void before() {
    final LogStream logStream = logStreamRule.getLogStream();
    logStreamReader.wrap(logStream);
  }

  @Override
  protected void after() {
    logStreamReader.close();
  }

  public LogStreamReader getLogStreamReader() {
    return logStreamReader;
  }

  public LoggedEvent assertEvents(final int eventCount, final DirectBuffer event) {
    LoggedEvent lastEvent = null;

    for (int i = 1; i <= eventCount; i++) {
      lastEvent = nextEvent();
      assertThat(lastEvent.getKey()).isEqualTo(i);
      assertThat(eventValue(lastEvent)).isEqualTo(event);
    }

    return lastEvent;
  }

  public LoggedEvent nextEvent() {
    assertThat(logStreamReader.hasNext()).isTrue();
    return logStreamReader.next();
  }

  public LoggedEvent readEventAtPosition(long position) {
    while (logStreamReader.hasNext()) {
      final LoggedEvent event = logStreamReader.next();
      if (event.getPosition() == position) {
        return event;
      }
    }
    return null;
  }

  private DirectBuffer eventValue(final LoggedEvent event) {
    assertThat(event).isNotNull();
    return new UnsafeBuffer(event.getValueBuffer(), event.getValueOffset(), event.getValueLength());
  }
}
