/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.broker.jobstream.ImmutableStreamRegistry.StreamConsumer;
import io.camunda.zeebe.broker.jobstream.ImmutableStreamRegistry.StreamId;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.stream.JobStreamTopics;
import io.camunda.zeebe.protocol.impl.stream.PushStreamRequest;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.stream.api.ActivatedJob;
import io.camunda.zeebe.stream.api.JobActivationProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

final class JobGatewayStreamerTest {
  private final ClusterEventService eventService = Mockito.mock(ClusterEventService.class);
  private final ClusterCommunicationService communicationService =
      Mockito.mock(ClusterCommunicationService.class);
  private final TestRegistry registry = new TestRegistry();

  private final JobGatewayStreamer streamer =
      new JobGatewayStreamer(eventService, communicationService, registry);

  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  @BeforeEach
  void beforeEach() {
    scheduler.submitActor(streamer);
    scheduler.workUntilDone();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(
        () -> {
          streamer.closeAsync();
          scheduler.workUntilDone();
        });
  }

  @Test
  void shouldNotifyAvailableJobsWhenNoConsumers() {
    // given
    final var type = BufferUtil.wrapString("foo");

    // when
    final var maybeStream = streamer.streamFor(type);
    scheduler.workUntilDone();

    // then
    assertThat(maybeStream).isEmpty();
    Mockito.verify(eventService, Mockito.times(1))
        .broadcast(JobStreamTopics.JOB_AVAILABLE.topic(), "foo");
  }

  @Test
  void shouldReportCorrectMetadata() {
    // given
    final var type = new UnsafeBuffer(BufferUtil.wrapString("foo"));
    final var streamId = new StreamId(UUID.randomUUID(), MemberId.from("a"));
    final var metadata = new TestActivationProperties();
    registry.consumers.put(type, Set.of(new StreamConsumer<>(streamId, metadata, type)));

    // when
    final var stream = streamer.streamFor(type).orElseThrow();

    // then
    assertThat(stream.metadata()).isSameAs(metadata);
  }

  @Test
  void shouldPush() {
    // given - a registry which returns a set of consumers sorted by their member IDs
    final var type = new UnsafeBuffer(BufferUtil.wrapString("foo"));
    final var streamId = new StreamId(UUID.randomUUID(), MemberId.from("a"));
    final var metadata = new TestActivationProperties();
    final var payload = new TestJob(1, new JobRecord());
    registry.consumers.put(type, Set.of(new StreamConsumer<>(streamId, metadata, type)));

    // when
    final var stream = streamer.streamFor(type).orElseThrow();
    stream.push(payload, (job, error) -> {});
    scheduler.workUntilDone();

    // then
    Mockito.verify(communicationService, Mockito.timeout(5_000).times(1))
        .send(
            Mockito.eq(JobStreamTopics.PUSH.topic()),
            Mockito.eq(new PushStreamRequest().streamId(streamId.streamId()).payload(payload)),
            Mockito.any(),
            Mockito.any(),
            Mockito.eq(streamId.receiver()),
            Mockito.any());
  }

  private record TestJob(long jobKey, JobRecord record) implements ActivatedJob {

    @Override
    public int getLength() {
      return 0;
    }

    @Override
    public void write(final MutableDirectBuffer buffer, final int offset) {}
  }

  private record TestRegistry(
      Map<DirectBuffer, Set<StreamConsumer<JobActivationProperties>>> consumers)
      implements ImmutableStreamRegistry<JobActivationProperties> {

    public TestRegistry() {
      this(new HashMap<>());
    }

    @Override
    public Set<StreamConsumer<JobActivationProperties>> get(final UnsafeBuffer streamType) {
      return consumers.getOrDefault(streamType, Collections.emptySet());
    }
  }

  private record TestActivationProperties(
      DirectBuffer worker, long timeout, Collection<DirectBuffer> fetchVariables)
      implements JobActivationProperties {

    private TestActivationProperties() {
      this(new UnsafeBuffer(), -1L, Collections.emptyList());
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {}
  }
}
