/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.ticktock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class TockResponderTest {

  private final ClusterCommunicationService communicationService =
      mock(ClusterCommunicationService.class);
  private final List<Tock> localTocks = new ArrayList<>();
  private final TockResponder responder =
      new TockResponder(communicationService, "1", localTocks::add);

  @Test
  void shouldTockOwnTickLocallyAsLeader() {
    // when
    responder.onTick(7, "1");

    // then
    assertThat(localTocks).containsExactly(new Tock("1", 7));
    verifyNoInteractions(communicationService);
  }

  @Test
  void shouldTockLeaderOverNetworkAsFollower() {
    // when
    responder.onTick(7, "0");

    // then
    assertThat(localTocks).isEmpty();
    verify(communicationService)
        .unicast(eq(Tock.SUBJECT), eq(new Tock("1", 7)), any(), eq(MemberId.from("0")), eq(true));
  }
}
