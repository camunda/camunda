/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.swim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.cluster.gossip.GossipCodec;
import io.camunda.zeebe.cluster.gossip.GossipKey;
import io.camunda.zeebe.cluster.gossip.MemberKey;
import io.camunda.zeebe.cluster.gossip.MergeStrategy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behaviour specific to the SWIM gossiper: the property format it shares with components that write
 * member properties directly, and its lack of GLOBAL keys. The general contract is checked by
 * {@link SwimClusterGossiperContractTest}.
 */
final class SwimClusterGossiperTest {

  private static final GossipCodec<String> STRING =
      new GossipCodec<>() {
        @Override
        public byte[] encode(final String value) {
          return value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String decode(final byte[] bytes) {
          return new String(bytes, StandardCharsets.UTF_8);
        }
      };

  private static final MemberKey<String> ROLE = GossipKey.member("test.role", STRING);

  private SwimTestCluster cluster;

  @BeforeEach
  void setUp() {
    cluster = new SwimTestCluster(3);
  }

  @AfterEach
  void tearDown() {
    cluster.close();
  }

  @Test
  void shouldWriteValueAsBase64PropertyNamedLikeKey() {
    // when
    cluster.gossiper(0).put(ROLE, "leader");

    // then - the format components already use when writing member properties directly
    assertThat(cluster.localProperties(0).getProperty(ROLE.name()))
        .isEqualTo(Base64.getEncoder().encodeToString(STRING.encode("leader")));
  }

  @Test
  void shouldReadValueWrittenDirectlyAsMemberProperty() {
    // given - a component writing its member property directly, as before the gossiper existed
    final var legacyKey = GossipKey.member("test.legacy", STRING);
    final var encoded = Base64.getEncoder().encodeToString(STRING.encode("written directly"));

    // when
    cluster.localProperties(0).setProperty(legacyKey.name(), encoded);

    // then
    await()
        .untilAsserted(
            () ->
                assertThat(cluster.gossiper(2).get(legacyKey, cluster.memberId(0)))
                    .contains("written directly"));
  }

  @Test
  void shouldIgnorePropertiesNoKeyRefersTo() {
    // given - another component's property, which is not even Base64
    final var received = new CopyOnWriteArrayList<String>();
    cluster
        .gossiper(2)
        .subscribe(ROLE, Runnable::run, (member, value) -> received.add(member + "=" + value));
    cluster.localProperties(0).setProperty("test.unrelated", "not Base64!");

    // when
    cluster.gossiper(0).put(ROLE, "leader");

    // then
    await()
        .untilAsserted(
            () ->
                assertThat(cluster.gossiper(2).get(ROLE, cluster.memberId(0))).contains("leader"));
    assertThat(received).containsExactly(cluster.memberId(0) + "=" + Optional.of("leader"));
  }

  @Test
  void shouldRejectGlobalKeys() {
    // given
    final var key = GossipKey.global("test.flag", STRING, MergeStrategy.lastWriterWins(), "off");
    final var gossiper = cluster.gossiper(0);

    // when - then
    assertThatThrownBy(() -> gossiper.get(key)).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> gossiper.put(key, "on"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> gossiper.update(key, value -> value))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> gossiper.subscribe(key, Runnable::run, value -> {}))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static ConditionFactory await() {
    return Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(20));
  }
}
