/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import io.camunda.secretstore.SecretCache;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.mockito.stubbing.Answer;

/**
 * Shared test support for {@code EngineRule}-based tests that verify secret resolution on job
 * activation: serves a controllable {@link SecretCache} and captures the resolved-secret {@link
 * JobBatchRecord} the mocked {@link CommandResponseWriter} receives.
 *
 * <p>This interception is not incidental test complexity — it is the only way to observe a resolved
 * secret value in an {@code EngineRule}-based test. By design (see {@code
 * JobBatchActivateProcessor#responseValueFor}), a resolved secret is injected only into the
 * transient long-poll response written via {@code CommandResponseWriter#valueWriter}; the
 * persisted/exported {@code JobBatchIntent.ACTIVATED} record — which is what {@code
 * JobActivationClient#activate()}'s own return value and {@code RecordingExporter} both read —
 * always keeps the unresolved placeholder. {@code EngineRule#getCommandResponseWriter()} returns a
 * bare Mockito mock with no built-in capture, so a test needs this interceptor to see the resolved
 * value at all.
 *
 * <p>Usage: construct one instance per test, register it as the {@code SecretCache} in the {@code
 * SecretStoreRegistry} passed to {@code EngineRule#withSecretStoreRegistry}, and call {@link
 * #install(CommandResponseWriter)} from a {@code @Before} method with {@code
 * engine.getCommandResponseWriter()}. If the {@code @Rule EngineRule} field references this
 * instance, assign it in the test class's constructor rather than in the field's own initializer:
 * all instance field initializers run before any constructor-body statement (JLS 12.5) regardless
 * of field declaration order, so a constructor-body assignment always sees this field already
 * constructed — which also lets {@code public} {@code @Rule} fields be declared before this {@code
 * private} one, satisfying Checkstyle's {@code DeclarationOrder} check.
 */
public final class SecretActivationResponseCapture implements SecretCache {

  private final Map<String, String> cachedSecrets = new ConcurrentHashMap<>();
  private volatile boolean failResolution;
  private volatile JobBatchRecord activationResponse;

  /** Installs the response-writer interception; call once, from a {@code @Before} method. */
  public void install(final CommandResponseWriter mockResponseWriter) {
    doAnswer(
            (Answer<CommandResponseWriter>)
                invocation -> {
                  final var arguments = invocation.getArguments();
                  if (arguments != null
                      && arguments.length == 1
                      && arguments[0] instanceof final JobBatchRecord jobBatchRecord) {
                    // copy the record: engine record objects are reused across commands, so the
                    // captured reference could otherwise be overwritten by a later command
                    final var copy = new JobBatchRecord();
                    final MutableDirectBuffer buffer =
                        new UnsafeBuffer(new byte[jobBatchRecord.getLength()]);
                    jobBatchRecord.write(buffer, 0);
                    copy.wrap(buffer);
                    activationResponse = copy;
                  }
                  return mockResponseWriter;
                })
        .when(mockResponseWriter)
        .valueWriter(any());
  }

  /** Makes a secret resolve to {@code value} for the rest of the test (until changed again). */
  public void putSecret(final String name, final String value) {
    cachedSecrets.put(name, value);
  }

  /** Simulates a broken cache: every lookup throws until set back to {@code false}. */
  public void failResolution(final boolean fail) {
    failResolution = fail;
  }

  /**
   * Blocks until the mocked response writer has received a batch, then returns it. For a test that
   * needs to poll on the *content* of a later response (e.g. after a second activation reuses this
   * same captured field), use {@link #getActivationResponse()} directly inside a custom {@code
   * Awaitility} assertion instead — this method only waits for non-null, so it would return
   * immediately with a stale earlier response.
   */
  public JobBatchRecord awaitActivationResponse() {
    Awaitility.await("until the activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(activationResponse).isNotNull());
    return activationResponse;
  }

  /** The most recently captured response, or {@code null} if none has been written yet. */
  public JobBatchRecord getActivationResponse() {
    return activationResponse;
  }

  @Override
  public Optional<String> get(final String name) {
    if (failResolution) {
      throw new IllegalStateException("resolver exploded");
    }
    return Optional.ofNullable(cachedSecrets.get(name));
  }

  @Override
  public void put(final String name, final String value) {
    cachedSecrets.put(name, value);
  }

  @Override
  public void remove(final String name) {
    cachedSecrets.remove(name);
  }
}
