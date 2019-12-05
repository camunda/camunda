/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.log;

import static io.zeebe.logstreams.log.LogStreamTest.writeEvent;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.SynchronousLogStream;
import io.zeebe.util.buffer.BufferUtil;
import java.io.IOException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class LogStreamDeleteTest {

  private long firstPosition;
  private long secondPosition;
  private long thirdPosition;
  private long fourthPosition;

  private final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final LogStreamRule logStreamRule =
      LogStreamRule.createRuleWithoutStarting(temporaryFolder);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(temporaryFolder).around(logStreamRule);

  @Before
  public void setUp() throws IOException {
    final int segmentSize = 1024;
    final int entrySize = Math.floorDiv(segmentSize, 2) + 1;

    final SynchronousLogStream logStream =
        logStreamRule.startLogStreamWithStorageConfiguration(
            b -> b.withMaxSegmentSize(segmentSize).withMaxEntrySize(entrySize));

    // remove some bytes for padding per entry
    final byte[] largeEvent = new byte[entrySize - 90];
    // written from segment 0 4096 -> 8192
    firstPosition = writeEvent(logStream, BufferUtil.wrapArray(largeEvent));
    // written from segment 1 4096 -> 8192
    secondPosition = writeEvent(logStream, BufferUtil.wrapArray(largeEvent));
    // written from segment 2 4096 -> 8192
    thirdPosition = writeEvent(logStream, BufferUtil.wrapArray(largeEvent));
    // written from segment 3 4096 -> 8192
    fourthPosition = writeEvent(logStream, BufferUtil.wrapArray(largeEvent));
  }

  @Test
  public void shouldDeleteFromLogStream() {
    // given
    final SynchronousLogStream logStream = logStreamRule.getLogStream();

    // when
    logStream.delete(fourthPosition);

    // then
    assertThat(events().count()).isEqualTo(1);

    assertThat(events().anyMatch(e -> e.getPosition() == firstPosition)).isFalse();
    assertThat(events().anyMatch(e -> e.getPosition() == secondPosition)).isFalse();
    assertThat(events().anyMatch(e -> e.getPosition() == thirdPosition)).isFalse();

    assertThat(events().findFirst().get().getPosition()).isEqualTo(fourthPosition);
  }

  @Test
  public void shouldNotDeleteOnNegativePosition() {
    // given
    final SynchronousLogStream logStream = logStreamRule.getLogStream();

    // when
    logStream.delete(-1);

    // then - segment 0 and 1 are removed
    assertThat(events().count()).isEqualTo(4);

    assertThat(events().filter(e -> e.getPosition() == firstPosition).findAny()).isNotEmpty();
    assertThat(events().filter(e -> e.getPosition() == secondPosition).findAny()).isNotEmpty();
    assertThat(events().filter(e -> e.getPosition() == thirdPosition).findAny()).isNotEmpty();
    assertThat(events().filter(e -> e.getPosition() == fourthPosition).findAny()).isNotEmpty();
  }

  private Stream<LoggedEvent> events() {
    final LogStreamReader reader = logStreamRule.getLogStreamReader();
    reader.seekToFirstEvent();
    final Iterable<LoggedEvent> iterable = () -> reader;
    return StreamSupport.stream(iterable.spliterator(), false);
  }
}
