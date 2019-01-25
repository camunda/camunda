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

package io.zeebe.client.impl;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.cmd.ClientStatusException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class ZeebeClientFutureImpl<ClientResponse, BrokerResponse>
    extends CompletableFuture<ClientResponse>
    implements ZeebeFuture<ClientResponse>, StreamObserver<BrokerResponse> {

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

  private RuntimeException transformExecutionException(ExecutionException e) {
    final Throwable cause = e.getCause();

    if (cause instanceof StatusRuntimeException) {
      final Status status = ((StatusRuntimeException) cause).getStatus();
      throw new ClientStatusException(status, e);
    } else {
      throw new ClientException(e);
    }
  }
}
