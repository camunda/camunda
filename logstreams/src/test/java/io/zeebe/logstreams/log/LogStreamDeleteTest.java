/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.log;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.HEADER_BLOCK_LENGTH;
import static io.zeebe.logstreams.log.LogStreamTest.writeEvent;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.logstreams.impl.log.fs.FsLogSegmentDescriptor;
import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.util.buffer.BufferUtil;
import java.io.File;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
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

  @Test
  public void shouldDeleteOnClose() {
    final File logDir = temporaryFolder.getRoot();
    final LogStream logStream =
        logStreamRule.startLogStreamWithConfiguration(
            b -> b.logRootPath(logDir.getAbsolutePath()).deleteOnClose(true));

    // when
    logStream.close();

    // then
    final File[] files = logDir.listFiles();
    assertThat(files).isNull();
  }

  @Test
  public void shouldNotDeleteOnCloseByDefault() {
    final File logDir = temporaryFolder.getRoot();
    final LogStream logStream =
        logStreamRule.startLogStreamWithConfiguration(b -> b.logRootPath(logDir.getAbsolutePath()));

    // when
    logStream.close();

    // then
    final File[] files = logDir.listFiles();
    assertThat(files).isNotNull();
    assertThat(files.length).isGreaterThan(0);
  }

  @Test
  public void shouldDeleteFromLogStream() {
    // given
    final LogStream logStream = prepareLogstream();

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
    final LogStream logStream = prepareLogstream();

    // when
    logStream.delete(-1);

    // then - segment 0 and 1 are removed
    assertThat(events().count()).isEqualTo(4);

    assertThat(events().filter(e -> e.getPosition() == firstPosition).findAny()).isNotEmpty();
    assertThat(events().filter(e -> e.getPosition() == secondPosition).findAny()).isNotEmpty();
    assertThat(events().filter(e -> e.getPosition() == thirdPosition).findAny()).isNotEmpty();
    assertThat(events().filter(e -> e.getPosition() == fourthPosition).findAny()).isNotEmpty();
  }

  private LogStream prepareLogstream() {
    final int segmentSize = 1024 * 8;
    final int remainingCapacity =
        (segmentSize
            - FsLogSegmentDescriptor.METADATA_LENGTH
            - alignedLength(HEADER_BLOCK_LENGTH + 2 + 8)
            - 1);
    final LogStream logStream =
        logStreamRule.startLogStreamWithConfiguration(
            c -> c.logSegmentSize(segmentSize).maxFragmentSize(segmentSize));
    final byte[] largeEvent = new byte[remainingCapacity];

    // written from segment 0 4096 -> 8192
    firstPosition = writeEvent(logStream, BufferUtil.wrapArray(largeEvent));
    // written from segment 1 4096 -> 8192
    secondPosition = writeEvent(logStream, BufferUtil.wrapArray(largeEvent));
    // written from segment 2 4096 -> 8192
    thirdPosition = writeEvent(logStream, BufferUtil.wrapArray(largeEvent));
    // written from segment 3 4096 -> 8192
    fourthPosition = writeEvent(logStream, BufferUtil.wrapArray(largeEvent));

    //    logStream.setCommitPosition(fourthPosition);

    return logStream;
  }

  private Stream<LoggedEvent> events() {
    final LogStreamReader reader = logStreamRule.getLogStreamReader();
    reader.seekToFirstEvent();
    final Iterable<LoggedEvent> iterable = () -> reader;
    return StreamSupport.stream(iterable.spliterator(), false);
  }
}
