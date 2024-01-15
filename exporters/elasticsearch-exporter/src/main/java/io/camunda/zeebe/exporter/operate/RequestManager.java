/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate;

import io.camunda.zeebe.exporter.ElasticsearchExporterException;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Function;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;

public class RequestManager {

  private Semaphore requestAccess;
  private LinkedList<InflightRequest> inflightRequests;
  private RestClient esRestClient;
  private Consumer<RuntimeException> errorHandler;
  private Function<Response, RuntimeException> errorMapper;
  private Function<Exception, RuntimeException> failureMapper;

  public RequestManager(
      RestClient esRestClient,
      Function<Response, RuntimeException> errorMapper,
      Function<Exception, RuntimeException> failureMapper,
      Consumer<RuntimeException> errorHandler,
      int numConcurrentRequests) {
    this.esRestClient = esRestClient;
    this.errorHandler = errorHandler;
    this.errorMapper = errorMapper;
    this.requestAccess = new Semaphore(numConcurrentRequests);
    this.inflightRequests = new LinkedList<>();
  }

  public void resolveResponses() {
    while (!inflightRequests.isEmpty()) {
      final InflightRequest request = inflightRequests.peek();
      if (!request.hasReturned()) {
        // stop on the first request that is still in progress to always
        // handle them in order
        return;
      }

      final RuntimeException requestError = request.getError();

      if (requestError == null) {

        inflightRequests.remove();

        request.getSuccessHandler().accept(request.getResponse());

      } else {
        errorHandler.accept(requestError);
        request.resetResponse();

        // and retry
        sendInflightRequest(request);
      }
    }
  }

  /** for testing */
  public void resolveResponsesBlocking(int attempts) {

    int currentAttempt = 0;
    while (!inflightRequests.isEmpty() && currentAttempt < attempts) {
      resolveResponses();
      currentAttempt++;
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /** Request itself is async but throttled (i.e. blocks if there are too many inflight requests) */
  public void send(Request request, Consumer<Response> successHandler) {

    final InflightRequest inflightRequest = new InflightRequest(request, successHandler);
    sendInflightRequest(inflightRequest);
    inflightRequests.add(inflightRequest);
  }

  private void sendInflightRequest(InflightRequest inflightRequest) {
    try {
      requestAccess.acquire();
    } catch (InterruptedException e) {
      throw new ElasticsearchExporterException("Could not acquire a concurrent request", e);
    }

    esRestClient.performRequestAsync(
        inflightRequest.getRequest(),
        new ResponseListener() {

          // TODO: both of the callbacks may be called, if onSuccess throws an exception
          // => can lead to more than x concurrent requests
          @Override
          public void onSuccess(Response response) {
            inflightRequest.setResponse(response);

            final RuntimeException mappedError = errorMapper.apply(response);
            inflightRequest.setError(mappedError);

            requestAccess.release();
          }

          @Override
          public void onFailure(Exception exception) {
            inflightRequest.setError(failureMapper.apply(exception));
            requestAccess.release();
          }
        });
  }

  private static class InflightRequest {
    private Request request;
    private volatile Response response;
    private volatile RuntimeException error;
    private Consumer<Response> successHandler;

    public InflightRequest(Request request, Consumer<Response> successHandler) {
      this.request = request;
      this.successHandler = successHandler;
    }

    public Request getRequest() {
      return request;
    }

    public Consumer<Response> getSuccessHandler() {
      return successHandler;
    }

    public RuntimeException getError() {
      return error;
    }

    public void setError(RuntimeException error) {
      this.error = error;
    }

    public Response getResponse() {
      return response;
    }

    public void setResponse(Response response) {
      this.response = response;
    }

    public boolean hasReturned() {
      return response != null || error != null;
    }
    
    public void resetResponse() {
      this.response = null;
      this.error = null;
    }
  }
}
