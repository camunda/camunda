/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.MessagingException.NoSuchMemberException;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.cluster.messaging.UnicastService;
import io.atomix.utils.net.Address;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultClusterCommunicationServiceTest {

  private static final String SUBJECT = "test-subject";
  private static final byte[] MESSAGE_BYTES = "test-message".getBytes();

  @Mock private ClusterMembershipService membershipService;
  @Mock private MessagingService messagingService;
  @Mock private UnicastService unicastService;

  private DefaultClusterCommunicationService service;
  private Address unknownAddress;
  private final Function<byte[], String> decoder = String::new;
  private final Function<String, byte[]> encoder = String::getBytes;

  @BeforeEach
  void setUp() {
    service =
        new DefaultClusterCommunicationService(membershipService, messagingService, unicastService);
    unknownAddress = new Address("unknown.host", 5000);
  }

  @Test
  void shouldRejectRequestWhenSenderNotKnownInResponder() {
    // given
    when(membershipService.getMember(unknownAddress)).thenReturn(null);

    final Function<String, CompletableFuture<String>> handler =
        msg -> CompletableFuture.completedFuture("response");

    service.replyTo(SUBJECT, decoder, handler, encoder);

    // when - invoke the registered handler directly
    final ArgumentCaptor<BiFunction<Address, byte[], CompletableFuture<byte[]>>> handlerCaptor =
        ArgumentCaptor.forClass(BiFunction.class);
    verify(messagingService).registerHandler(eq(SUBJECT), handlerCaptor.capture());

    final var result = handlerCaptor.getValue().apply(unknownAddress, MESSAGE_BYTES);

    // then
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(NoSuchMemberException.class)
        .hasMessageContaining(unknownAddress.toString());
  }

  @Test
  void shouldRejectRequestWhenSenderNotKnownInAsyncResponder() {
    // given
    when(membershipService.getMember(unknownAddress)).thenReturn(null);

    final Function<String, CompletableFuture<String>> handler =
        msg -> CompletableFuture.completedFuture("response");
    final Executor executor = Runnable::run;

    service.replyToAsync(SUBJECT, decoder, handler, encoder, executor);

    // when - invoke the registered handler directly
    final ArgumentCaptor<BiFunction<Address, byte[], CompletableFuture<byte[]>>> handlerCaptor =
        ArgumentCaptor.forClass(BiFunction.class);
    verify(messagingService).registerHandler(eq(SUBJECT), handlerCaptor.capture());

    final var result = handlerCaptor.getValue().apply(unknownAddress, MESSAGE_BYTES);

    // then
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(NoSuchMemberException.class)
        .hasMessageContaining(unknownAddress.toString());
  }

  @Test
  void shouldRejectRequestWhenSenderNotKnownInBiResponder() {
    // given
    when(membershipService.getMember(unknownAddress)).thenReturn(null);

    final BiFunction<MemberId, String, String> handler = (memberId, msg) -> "response";
    final Executor executor = Runnable::run;

    service.replyTo(SUBJECT, decoder, handler, encoder, executor);

    // when - invoke the registered handler directly
    final ArgumentCaptor<BiFunction<Address, byte[], CompletableFuture<byte[]>>> handlerCaptor =
        ArgumentCaptor.forClass(BiFunction.class);
    verify(messagingService).registerHandler(eq(SUBJECT), handlerCaptor.capture());

    final var result = handlerCaptor.getValue().apply(unknownAddress, MESSAGE_BYTES);

    // then
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(NoSuchMemberException.class)
        .hasMessageContaining(unknownAddress.toString());
  }

  @Test
  void shouldIgnoreMessageFromUnknownSenderInConsumer() {
    // given
    when(membershipService.getMember(unknownAddress)).thenReturn(null);

    @SuppressWarnings("unchecked")
    final Consumer<String> handler = mock(Consumer.class);
    final Executor executor = Runnable::run;

    service.consume(SUBJECT, decoder, handler, executor);

    // when - invoke the registered handler directly
    final ArgumentCaptor<BiConsumer<Address, byte[]>> handlerCaptor =
        ArgumentCaptor.forClass(BiConsumer.class);
    verify(messagingService).registerHandler(eq(SUBJECT), handlerCaptor.capture(), eq(executor));

    handlerCaptor.getValue().accept(unknownAddress, MESSAGE_BYTES);

    // then
    verify(handler, never()).accept(any());
  }

  @Test
  void shouldIgnoreMessageFromUnknownSenderInBiConsumer() {
    // given
    when(membershipService.getMember(unknownAddress)).thenReturn(null);

    @SuppressWarnings("unchecked")
    final BiConsumer<MemberId, String> handler = mock(BiConsumer.class);
    final Executor executor = Runnable::run;

    service.consume(SUBJECT, decoder, handler, executor);

    // when - invoke the registered handler directly
    final ArgumentCaptor<BiConsumer<Address, byte[]>> handlerCaptor =
        ArgumentCaptor.forClass(BiConsumer.class);
    verify(messagingService).registerHandler(eq(SUBJECT), handlerCaptor.capture(), eq(executor));

    handlerCaptor.getValue().accept(unknownAddress, MESSAGE_BYTES);

    // then
    verify(handler, never()).accept(any(), any());
  }
}
