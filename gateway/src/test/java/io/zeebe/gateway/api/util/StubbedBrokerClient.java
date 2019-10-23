/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.cmd.BrokerErrorException;
import io.zeebe.gateway.cmd.BrokerRejectionException;
import io.zeebe.gateway.cmd.BrokerResponseException;
import io.zeebe.gateway.cmd.IllegalBrokerResponseException;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.BrokerResponseConsumer;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class StubbedBrokerClient implements BrokerClient {

  BrokerTopologyManager topologyManager = new StubbedTopologyManager();
  private Consumer<String> jobsAvailableHandler;

  private Map<Class<?>, RequestHandler> requestHandlers = new HashMap<>();

  private List<BrokerRequest> brokerRequests = new ArrayList<>();

  public StubbedBrokerClient() {}

  @Override
  public void close() {}

  @Override
  public <T> ActorFuture<BrokerResponse<T>> sendRequest(BrokerRequest<T> request) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public <T> void sendRequest(
      BrokerRequest<T> request,
      BrokerResponseConsumer<T> responseConsumer,
      Consumer<Throwable> throwableConsumer) {
    brokerRequests.add(request);
    try {
      final RequestHandler requestHandler = requestHandlers.get(request.getClass());
      final BrokerResponse<T> response = requestHandler.handle(request);
      if (response.isResponse()) {
        responseConsumer.accept(response.getKey(), response.getResponse());
      } else if (response.isRejection()) {
        throwableConsumer.accept(new BrokerRejectionException(response.getRejection()));
      } else if (response.isError()) {
        throwableConsumer.accept(new BrokerErrorException(response.getError()));
      } else {
        throwableConsumer.accept(
            new IllegalBrokerResponseException("Unknown response received: " + response));
      }
    } catch (Exception e) {
      throwableConsumer.accept(new BrokerResponseException(e));
    }
  }

  @Override
  public <T> ActorFuture<BrokerResponse<T>> sendRequest(
      BrokerRequest<T> request, Duration requestTimeout) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public <T> void sendRequest(
      BrokerRequest<T> request,
      BrokerResponseConsumer<T> responseConsumer,
      Consumer<Throwable> throwableConsumer,
      Duration requestTimeout) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public BrokerTopologyManager getTopologyManager() {
    return topologyManager;
  }

  @Override
  public void subscribeJobAvailableNotification(String topic, Consumer<String> handler) {
    this.jobsAvailableHandler = handler;
  }

  public <RequestT extends BrokerRequest<?>, ResponseT extends BrokerResponse<?>>
      void registerHandler(
          Class<?> requestType, RequestHandler<RequestT, ResponseT> requestHandler) {
    requestHandlers.put(requestType, requestHandler);
  }

  public void notifyJobsAvailable(String type) {
    jobsAvailableHandler.accept(type);
  }

  public <T extends BrokerRequest<?>> T getSingleBrokerRequest() {
    assertThat(brokerRequests).hasSize(1);
    return (T) brokerRequests.get(0);
  }

  public interface RequestStub<
          RequestT extends BrokerRequest<?>, ResponseT extends BrokerResponse<?>>
      extends RequestHandler<RequestT, ResponseT> {
    void registerWith(StubbedBrokerClient gateway);
  }

  @FunctionalInterface
  interface RequestHandler<RequestT extends BrokerRequest<?>, ResponseT extends BrokerResponse<?>> {
    ResponseT handle(RequestT request) throws Exception;
  }
}
