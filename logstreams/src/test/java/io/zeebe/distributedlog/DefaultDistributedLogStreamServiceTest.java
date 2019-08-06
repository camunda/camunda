/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.zeebe.distributedlog.impl.LogstreamConfig;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class DefaultDistributedLogStreamServiceTest {

  private static final ServiceName<LogStream> LOG_STREAM_SERVICE_NAME =
      LogStreamServiceNames.logStreamServiceName("raft-atomix-partition-1");
  private static final List<String> MEMBERS = Arrays.asList("1");
  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
  private final ServiceContainerRule serviceContainerRule =
      new ServiceContainerRule(actorSchedulerRule);
  private final LogInstallRule installRule = new LogInstallRule();
  private final DistributedLogRule distributedLogRule =
      new DistributedLogRule(serviceContainerRule, 1, 1, 1, MEMBERS, null);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(actorSchedulerRule)
          .around(serviceContainerRule)
          .around(temporaryFolder)
          .around(installRule)
          .around(distributedLogRule);

  @Test
  public void shouldAppendBlock() throws Exception {
    // given
    distributedLogRule.waitUntilNodesJoined();
    distributedLogRule.becomeLeader(1);
    final LogStream logStreamSpy = installRule.getService().getLogStreamMock();

    // when
    distributedLogRule.writeEvent(1, "message");

    // then
    verify(logStreamSpy.getLogStorage(), timeout(5_000).times(1)).append(any(ByteBuffer.class));
  }

  @Test
  public void shouldRetryAppendBlock() throws Exception {
    // given
    distributedLogRule.waitUntilNodesJoined();
    distributedLogRule.becomeLeader(1);
    final LogStream logStreamSpy = installRule.getService().getLogStreamMock();
    final LogStorage logStorage = logStreamSpy.getLogStorage();
    doThrow(IOException.class).when(logStorage).append(any());

    // when
    distributedLogRule.writeEvent(1, "message");

    // then
    verify(logStreamSpy.getLogStorage(), timeout(5_000).atLeast(2)).append(any(ByteBuffer.class));
  }

  @Test
  public void shouldBlockPrimitive() throws Exception {
    // given
    distributedLogRule.waitUntilNodesJoined();
    distributedLogRule.becomeLeader(1);
    final LogStream logStreamSpy = installRule.getService().getLogStreamMock();
    final LogStorage logStorage = logStreamSpy.getLogStorage();
    doThrow(IllegalStateException.class).when(logStorage).append(any());

    // when
    distributedLogRule.writeEvent(1, "message");
    distributedLogRule.writeEvent(1, "message");

    // then
    verify(logStreamSpy.getLogStorage(), timeout(5_000).times(1)).append(any(ByteBuffer.class));
  }

  private class LogInstallRule extends ExternalResource {

    private LogService service;

    @Override
    protected void before() {
      final ServiceContainer serviceContainer = serviceContainerRule.get();
      final LogStream logStream =
          LogStreams.createFsLogStream(1)
              .logDirectory(temporaryFolder.getRoot().getAbsolutePath())
              .logSegmentSize(512 * 1024 * 1024)
              .serviceContainer(serviceContainer)
              .build()
              .join();

      service = new LogService(logStream);
      serviceContainer.createService(LOG_STREAM_SERVICE_NAME, service).install().join();

      LogstreamConfig.putLogStream("1", 1, service.logStreamMock);
    }

    @Override
    protected void after() {
      serviceContainerRule.get().removeService(LOG_STREAM_SERVICE_NAME);
    }

    public LogService getService() {
      return service;
    }
  }

  private class LogService implements Service<LogStream> {

    private final LogStream logStreamMock;

    LogService(LogStream logStream) {
      this.logStreamMock = spy(logStream);
      final LogStorage logStorageSpy = spy(logStreamMock.getLogStorage());
      doReturn(logStorageSpy).when(logStreamMock).getLogStorage();
      doReturn("raft-atomix-partition-1").when(logStreamMock).getLogName();
    }

    @Override
    public LogStream get() {
      return logStreamMock;
    }

    public LogStream getLogStreamMock() {
      return logStreamMock;
    }
  }
}
