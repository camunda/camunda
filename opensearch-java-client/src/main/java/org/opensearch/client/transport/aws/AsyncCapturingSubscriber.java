/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.client.transport.aws;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * A reactive subscriber that captures a byte stream into a byte array
 */
class AsyncCapturingSubscriber implements Subscriber<ByteBuffer> {
    private final ByteArrayOutputStream buffer;
    private final CompletableFuture<byte[]> promise;
    private Subscription subscription;

    AsyncCapturingSubscriber() {
        buffer = new ByteArrayOutputStream();
        promise = new CompletableFuture<>();
    }

    public CompletableFuture<byte[]> getPromise() {
        return promise;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(ByteBuffer buf) {
        try {
            if (buf != null && buf.remaining() > 0) {
                if (buf.hasArray()) {
                    buffer.write(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
                } else {
                    byte[] data = new byte[buf.remaining()];
                    buf.asReadOnlyBuffer().get(data);
                    buffer.write(data);
                }
            }
            this.subscription.request(1);
        } catch (Throwable e) {
            promise.completeExceptionally(e);
        }
    }

    @Override
    public void onError(Throwable e) {
        if (e == null) {
            e = new IllegalArgumentException("Subscriber.onError called with null paramter");
        }
        promise.completeExceptionally(e);
    }

    @Override
    public void onComplete() {
        promise.complete(buffer.toByteArray());
    }
}
