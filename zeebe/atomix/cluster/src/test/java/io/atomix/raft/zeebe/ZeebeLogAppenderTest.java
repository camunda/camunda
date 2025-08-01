/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.zeebe;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Stopwatch;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.zeebe.util.TestAppender;
import io.atomix.raft.zeebe.util.ZeebeTestHelper;
import io.atomix.raft.zeebe.util.ZeebeTestNode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.AutoClose;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tests the {@link io.atomix.raft.roles.LeaderRole} implementation of {@link ZeebeLogAppender} */
public class ZeebeLogAppenderTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @AutoClose MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final Stopwatch stopwatch = Stopwatch.createUnstarted();
  private final TestAppender appenderListener = new TestAppender();

  private ZeebeTestNode node;
  private ZeebeTestHelper helper;

  @Before
  public void setUp() throws Exception {
    node = new ZeebeTestNode(0, temporaryFolder.newFolder("0"), meterRegistry);

    final Set<ZeebeTestNode> nodes = Collections.singleton(node);
    helper = new ZeebeTestHelper(nodes);

    node.start(nodes).join();
    stopwatch.start();
  }

  @After
  public void tearDown() {
    if (stopwatch.isRunning()) {
      stopwatch.stop();
    }

    logger.info("Test run time: {}", stopwatch.toString());
    node.stop().join();
  }

  @Test
  public void shouldNotifyOnWrite() {
    // when
    append();

    // then
    final IndexedRaftLogEntry appended = appenderListener.pollWritten();
    assertThat(appended).isNotNull();
    assertThat(appenderListener.getErrors().size()).isEqualTo(0);
  }

  @Test
  public void shouldNotifyOnCommit() {
    // when
    append();

    // then
    final var committed = appenderListener.pollCommitted();
    assertThat(committed).isNotNull();
    assertThat(appenderListener.getErrors().size()).isEqualTo(0);
  }

  @Test
  public void shouldNotifyOnError() {
    // given - a message that cannot be appended because it's too large
    final ByteBuffer data = ByteBuffer.allocate(2048);

    // when
    append(data);

    // then
    final Throwable error = appenderListener.pollError();
    assertThat(error).isNotNull();
    assertThat(appenderListener.getWritten().size()).isEqualTo(0L);
    assertThat(appenderListener.getCommitted().size()).isEqualTo(0L);
  }

  private void append() {
    append(ByteBuffer.allocate(Integer.BYTES).putInt(0, 1));
  }

  private void append(final ByteBuffer data) {
    final ZeebeLogAppender appender = helper.awaitLeaderAppender(1);
    appender.appendEntry(0, 0, data, appenderListener);
  }
}
