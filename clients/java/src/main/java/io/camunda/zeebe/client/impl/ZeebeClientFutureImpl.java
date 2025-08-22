/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.camunda.zeebe.client.impl;

import com.google.protobuf.GeneratedMessage;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class ZeebeClientFutureImpl<ClientResponse, BrokerResponse>
    extends CompletableFuture<ClientResponse>
    implements ZeebeFuture<ClientResponse>,
        ClientResponseObserver<GeneratedMessage, BrokerResponse> {

  protected ClientCallStreamObserver<GeneratedMessage> clientCall;
  private final Function<BrokerResponse, ClientResponse> responseMapper;

  public ZeebeClientFutureImpl() {
    this(brokerResponse -> null);
  }

  public ZeebeClientFutureImpl(final Function<BrokerResponse, ClientResponse> responseMapper) {
    this.responseMapper = responseMapper;
  }

  @Override
  public ClientResponse join() {
    try {
      return get();
    } catch (final ExecutionException e) {
      throw transformExecutionException(e);
    } catch (final InterruptedException e) {
      throw new ClientException("Unexpectedly interrupted awaiting client response", e);
    }
  }

  @Override
  public boolean cancel(final boolean mayInterruptIfRunning) {
    return cancel(mayInterruptIfRunning, null);
  }

  @Override
  public boolean cancel(final boolean mayInterruptIfRunning, final Throwable cause) {
    if (mayInterruptIfRunning && clientCall != null) {
      clientCall.cancel("Client call explicitly cancelled by user", cause);
      return true;
    } else {
      return super.cancel(mayInterruptIfRunning);
    }
  }

  @Override
  public ClientResponse join(final long timeout, final TimeUnit unit) {
    try {
      return get(timeout, unit);
    } catch (final ExecutionException e) {
      throw transformExecutionException(e);
    } catch (final InterruptedException e) {
      throw new ClientException("Unexpectedly interrupted awaiting client response", e);
    } catch (final TimeoutException e) {
      throw new ClientException("Timed out waiting on client response", e);
    }
  }

  @Override
  public void onNext(final BrokerResponse brokerResponse) {
    try {
      complete(responseMapper.apply(brokerResponse));
    } catch (final Exception e) {
      completeExceptionally(e);
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    completeExceptionally(throwable);
  }

  @Override
  public void onCompleted() {
    // do nothing as we don't support streaming
  }

  @Override
  public void beforeStart(final ClientCallStreamObserver<GeneratedMessage> requestStream) {
    if (isDone()) {
      requestStream.cancel("Call was completed by the client before it was started", null);
      return;
    }

    clientCall = requestStream;
  }

  private RuntimeException transformExecutionException(final ExecutionException e) {
    final Throwable cause = e.getCause();

    if (cause instanceof StatusRuntimeException) {
      final Status status = ((StatusRuntimeException) cause).getStatus();
      throw new ClientStatusException(status, e);
    } else {
      throw new ClientException(e);
    }
  }
}
