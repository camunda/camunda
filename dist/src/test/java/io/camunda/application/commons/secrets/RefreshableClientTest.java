/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RefreshableClientTest {

  @Test
  void shouldSwapToANewClientAndThenDrainAndCloseTheOldOne() throws Exception {
    // given
    final var lifecycle = new RecordingLifecycle();
    final var client = RefreshableClient.of("test", "v1", lifecycle);
    final var old = client.client();

    // when
    client.refresh("v2");

    // then
    assertThat(client.client().settings()).isEqualTo("v2");
    assertThat(client.settings()).isEqualTo("v2");
    assertThat(lifecycle.events)
        .containsExactly("build v1", "build v2", "verify v2", "drain v1", "close v1");
    assertThat(old.closed).isTrue();
  }

  @Test
  void shouldKeepTheOldClientWhenTheNewOneFailsToVerify() {
    // given
    final var lifecycle = new RecordingLifecycle();
    lifecycle.failVerify = true;
    final var client = RefreshableClient.of("test", "v1", lifecycle);
    final var old = client.client();

    // when / then
    assertThatThrownBy(() -> client.refresh("v2")).hasMessage("cannot connect with v2");
    assertThat(client.client()).isSameAs(old);
    assertThat(client.settings()).isEqualTo("v1");
    assertThat(old.closed).isFalse();
    assertThat(lifecycle.events).containsExactly("build v1", "build v2", "verify v2", "close v2");
  }

  @Test
  void shouldDoNothingWhenTheSettingsAreUnchanged() throws Exception {
    // given
    final var lifecycle = new RecordingLifecycle();
    final var client = RefreshableClient.of("test", "v1", lifecycle);

    // when
    client.refresh("v1");

    // then
    assertThat(lifecycle.events).containsExactly("build v1");
  }

  @Test
  void shouldKeepTheClientWhenItCanBeUpdatedInPlace() throws Exception {
    // given
    final var lifecycle = new RecordingLifecycle();
    lifecycle.inPlace = true;
    final var client = RefreshableClient.of("test", "v1", lifecycle);
    final var old = client.client();

    // when
    client.refresh("v2");

    // then
    assertThat(client.client()).isSameAs(old);
    assertThat(client.settings()).isEqualTo("v2");
    assertThat(lifecycle.events).containsExactly("build v1", "update v1 to v2");
  }

  @Test
  void shouldCloseTheCurrentClientOnClose() {
    // given
    final var lifecycle = new RecordingLifecycle();
    final var client = RefreshableClient.of("test", "v1", lifecycle);

    // when
    client.close();

    // then
    assertThat(client.client().closed).isTrue();
    assertThatThrownBy(() -> client.refresh("v2")).isInstanceOf(IllegalStateException.class);
  }

  private static final class FakeClient {

    private final String settings;
    private boolean closed;

    private FakeClient(final String settings) {
      this.settings = settings;
    }

    private String settings() {
      return settings;
    }
  }

  private static final class RecordingLifecycle
      implements RefreshableClient.Lifecycle<String, FakeClient> {

    private final List<String> events = new ArrayList<>();
    private boolean failVerify;
    private boolean inPlace;

    @Override
    public FakeClient build(final String settings) {
      events.add("build " + settings);
      return new FakeClient(settings);
    }

    @Override
    public void verify(final FakeClient client) {
      events.add("verify " + client.settings);
      if (failVerify) {
        throw new IllegalStateException("cannot connect with " + client.settings);
      }
    }

    @Override
    public boolean updateInPlace(final FakeClient client, final String current, final String next) {
      if (inPlace) {
        events.add("update " + current + " to " + next);
      }
      return inPlace;
    }

    @Override
    public void drain(final FakeClient client) {
      events.add("drain " + client.settings);
    }

    @Override
    public void close(final FakeClient client) {
      events.add("close " + client.settings);
      client.closed = true;
    }
  }
}
