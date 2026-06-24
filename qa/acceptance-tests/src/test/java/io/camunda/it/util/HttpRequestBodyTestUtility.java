/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.util;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;

public class HttpRequestBodyTestUtility {
  public static String extractBody(final HttpRequest httpRequest) {
    return httpRequest
        .bodyPublisher()
        .map(
            p -> {
              final var bodySubscriber =
                  HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
              final var flowSubscriber =
                  new HttpRequestBodyTestUtility.StringSubscriber(bodySubscriber);
              p.subscribe(flowSubscriber);
              return bodySubscriber.getBody().toCompletableFuture().join();
            })
        .orElseThrow();
  }

  static final class StringSubscriber implements Flow.Subscriber<ByteBuffer> {
    final HttpResponse.BodySubscriber<String> wrapped;

    StringSubscriber(final HttpResponse.BodySubscriber<String> wrapped) {
      this.wrapped = wrapped;
    }

    @Override
    public void onSubscribe(final Flow.Subscription subscription) {
      wrapped.onSubscribe(subscription);
    }

    @Override
    public void onNext(final ByteBuffer item) {
      wrapped.onNext(List.of(item));
    }

    @Override
    public void onError(final Throwable throwable) {
      wrapped.onError(throwable);
    }

    @Override
    public void onComplete() {
      wrapped.onComplete();
    }
  }
}
