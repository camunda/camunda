/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.jobstream;

import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.shared.management.JobStreamEndpoint.ClientJobStream;
import io.camunda.zeebe.shared.management.JobStreamEndpoint.RemoteJobStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.condition.AllOf;

/**
 * Convenience class to assert values obtained through a {@link
 * io.camunda.zeebe.qa.util.actuator.JobStreamActuator}.
 */
public final class JobStreamActuatorAssert
    extends AbstractObjectAssert<JobStreamActuatorAssert, JobStreamActuator> {
  /**
   * @param jobStreamActuator the actual actuator to assert against
   */
  public JobStreamActuatorAssert(final JobStreamActuator jobStreamActuator) {
    super(jobStreamActuator, JobStreamActuatorAssert.class);
  }

  /**
   * A convenience factory method that's consistent with AssertJ conventions.
   *
   * @param actual the actual client to assert against
   * @return an instance of {@link JobStreamActuatorAssert} to assert properties of the given client
   */
  public static JobStreamActuatorAssert assertThat(final JobStreamActuator actual) {
    return new JobStreamActuatorAssert(actual);
  }

  /** Returns collection assertions on the underlying client job streams. */
  public ClientJobStreamsAssert clientStreams() {
    isNotNull();
    return new ClientJobStreamsAssert(actual.listClient());
  }

  /** Returns collection assertions on the underlying remote job streams. */
  public RemoteJobStreamsAssert remoteStreams() {
    isNotNull();
    return new RemoteJobStreamsAssert(actual.listRemote());
  }

  @SuppressWarnings("UnusedReturnValue")
  public static final class ClientJobStreamsAssert
      extends AbstractJobStreamsAssert<ClientJobStreamsAssert, ClientJobStream> {

    public ClientJobStreamsAssert(final Collection<ClientJobStream> actual) {
      super(actual, ClientJobStreamsAssert.class);
    }

    /**
     * Asserts that the given service contains exactly {@code expectedCount} streams with the given
     * job type and the expected connected nodes.
     *
     * @param expectedCount the exact count of streams to find
     * @param jobType the expected type of the streams
     * @param nodeId the IDs of the nodes the stream should be connected to
     * @return itself for chaining
     */
    public ClientJobStreamsAssert haveConnectedTo(
        final int expectedCount, final String jobType, final int... nodeId) {
      return haveExactly(expectedCount, AllOf.allOf(hasJobType(jobType), isConnectedTo(nodeId)));
    }

    @Override
    protected ClientJobStreamsAssert newAbstractIterableAssert(
        final Iterable<? extends ClientJobStream> iterable) {
      final List<ClientJobStream> collection =
          StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
      return new ClientJobStreamsAssert(collection);
    }
  }

  @SuppressWarnings("UnusedReturnValue")
  public static final class RemoteJobStreamsAssert
      extends AbstractJobStreamsAssert<RemoteJobStreamsAssert, RemoteJobStream> {

    public RemoteJobStreamsAssert(final Collection<RemoteJobStream> actual) {
      super(actual, RemoteJobStreamsAssert.class);
    }

    /**
     * Asserts that the given service contains exactly {@code expectedCount} streams with the given
     * job type and the consumer count.
     *
     * @param expectedCount the exact count of streams to find
     * @param jobType the expected type of the streams
     * @param consumerCount the expected consumer count
     * @return itself for chaining
     */
    public RemoteJobStreamsAssert haveConsumerCount(
        final int expectedCount, final String jobType, final int consumerCount) {
      return haveExactly(
          expectedCount, AllOf.allOf(hasJobType(jobType), hasConsumerCount(consumerCount)));
    }

    /**
     * Asserts that the given service contains exactly {@code expectedCount} streams with the given
     * job type and the expected consumer receivers (in any order).
     *
     * @param expectedCount the exact count of streams to find
     * @param jobType the expected type of the streams
     * @param receivers the expected consumer receivers
     * @return itself for chaining
     */
    public RemoteJobStreamsAssert haveConsumerReceiver(
        final int expectedCount, final String jobType, final String... receivers) {
      final var collection = Arrays.asList(receivers);
      return haveExactly(
          expectedCount, AllOf.allOf(hasJobType(jobType), hasConsumerReceivers(collection)));
    }

    @Override
    protected RemoteJobStreamsAssert newAbstractIterableAssert(
        final Iterable<? extends RemoteJobStream> iterable) {
      final List<RemoteJobStream> collection =
          StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
      return new RemoteJobStreamsAssert(collection);
    }
  }
}
