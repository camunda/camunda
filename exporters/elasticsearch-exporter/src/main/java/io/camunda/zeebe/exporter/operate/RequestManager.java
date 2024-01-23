/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate;

import io.camunda.zeebe.exporter.ElasticsearchExporterException;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.prometheus.client.Histogram.Timer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(RequestManager.class);

  private LinkedList<InflightRequest> preparingRequests = new LinkedList<>();
  private InflightRequest inflightRequest;
  private RestClient esRestClient;
  private Consumer<Exception> errorHandler;
  private Function<Response, Exception> errorMapper;
  private Function<Exception, Exception> failureMapper;
  private OperateElasticsearchMetrics metrics;

  private ExecutorService jsonConverterPool;

  public RequestManager(
      RestClient esRestClient,
      Function<Response, Exception> errorMapper,
      Function<Exception, Exception> failureMapper,
      Consumer<Exception> errorHandler,
      int numSerializationThreads) {
    this.esRestClient = esRestClient;
    this.errorMapper = errorMapper;
    this.failureMapper = failureMapper;
    this.errorHandler = errorHandler;
    this.jsonConverterPool =
        Executors.newFixedThreadPool(
            numSerializationThreads, new NamedThreadFactory("json-serializer"));
  }

  public void setMetrics(OperateElasticsearchMetrics metrics) {
    this.metrics = metrics;
  }

  public void close() {
    this.jsonConverterPool.shutdown();
    metrics.recordJsonProcessingQueueSize(0);
  }

  public void eventLoop() {

    resolveNextAvailableRequest();

    sendNextAvailableRequest();
  }

  public void resolveNextAvailableRequest() {
    if (inflightRequest != null) {

      final CompletableFuture<Response> responseFuture = inflightRequest.getResponseFuture();

      if (responseFuture.isDone() || preparingRequests.size() >= 10) {
        handleResponse(inflightRequest);
      }
    }
  }

  private void handleResponse(InflightRequest request) {
    try {
      // we don't care for the response content in the happy case that
      // no exception is thrown
      final Response response = request.getResponseFuture().get();

      request.getSuccessHandler().accept(response);
      this.inflightRequest = null;

    } catch (Exception e) {
      errorHandler.accept(e);
      request.resetResponse();

      // and retry
      sendInflightRequest(request);
    }
  }

  /** for testing */
  public void eventLoop(int iterations) {

    int currentAttempt = 0;
    while ((!preparingRequests.isEmpty() || inflightRequest != null)
        && currentAttempt < iterations) {
      eventLoop();
      currentAttempt++;
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public void sendNextAvailableRequest() {
    if (!preparingRequests.isEmpty() && inflightRequest == null) {
      final InflightRequest nextRequest = preparingRequests.peek();

      if (nextRequest.isConverted()) {

        final Exception conversionError = nextRequest.getConversionError();
        if (conversionError != null) {
          // note: this will currently let the exporter loop indefinitely, but as this is
          // not expected to occur in the prototype, it should be fine => study logs
          throw new ElasticsearchExporterException(
              "Could not convert export request to JSON", conversionError);
        }

        preparingRequests.remove();
        metrics.recordJsonProcessingQueueSize(preparingRequests.size());

        sendInflightRequest(nextRequest);
      }
    }
  }

  /** Request itself is async but throttled (i.e. blocks if there are too many inflight requests) */
  public void send(Request request, Consumer<Response> successHandler) {

    final InflightRequest inflightRequest = new InflightRequest(request, successHandler);
    submitInflightRequest(inflightRequest);
  }

  private void submitInflightRequest(InflightRequest inflightRequest) {

    jsonConverterPool.submit(new JsonConverter(inflightRequest, metrics));
    preparingRequests.add(inflightRequest);
    metrics.recordJsonProcessingQueueSize(preparingRequests.size());
  }

  private void sendInflightRequest(InflightRequest inflightRequest) {

    final Timer requestTimer = metrics.measureRequestDuration();

    esRestClient.performRequestAsync(
        inflightRequest.getRequest(),
        new ResponseListener() {

          // TODO: both of the callbacks may be called, if onSuccess throws an exception
          // => can lead to more than x concurrent requests
          @Override
          public void onSuccess(Response response) {
            requestTimer.close();

            final CompletableFuture<Response> responseFuture = inflightRequest.getResponseFuture();

            final Exception mappedError = errorMapper.apply(response);

            if (mappedError != null) {
              responseFuture.completeExceptionally(mappedError);
            } else {
              responseFuture.complete(response);
            }
          }

          @Override
          public void onFailure(Exception exception) {
            requestTimer.close();

            final CompletableFuture<Response> responseFuture = inflightRequest.getResponseFuture();

            if (responseFuture.isDone()) {
              LOGGER.warn("Response failure callback was invoked after success callback");
            }

            responseFuture.completeExceptionally(failureMapper.apply(exception));
          }
        });

    this.inflightRequest = inflightRequest;
  }

  private static class JsonConverter implements Runnable {

    private InflightRequest request;
    private OperateElasticsearchMetrics metrics;

    public JsonConverter(InflightRequest request, OperateElasticsearchMetrics metrics) {
      this.request = request;
      this.metrics = metrics;
    }

    @Override
    public void run() {
      final HttpEntity requestEntity = request.getRequest().getEntity();

      final ByteArrayOutputStream outStream;
      try (Timer timer = metrics.measureSerializationDuration()) {
        // TODO: do we want to guess a size here? or reuse buffers?
        outStream = new ByteArrayOutputStream();
        try {
          requestEntity.writeTo(outStream);
        } catch (IOException e) {
          request.setConversionError(e);
          request.setConverted(true);
          return;
        }
      }

      // TODO: not the cleanest solution to replace the entity
      final byte[] byteArray = outStream.toByteArray();

      if (byteArray.length == 0) {
        throw new RuntimeException("request body is empty");
      }

      metrics.recordBulkMemorySize(byteArray.length);

      final ByteArrayEntity serializedBody = new ByteArrayEntity(byteArray);
      serializedBody.setContentType(requestEntity.getContentType());

      request.getRequest().setEntity(serializedBody);
      request.setConverted(true);

      metrics.recordCurrentThreadCpuTime();
    }
  }

  private static class InflightRequest {
    private Request request;

    private volatile boolean converted = false;
    private volatile Exception conversionError;

    private CompletableFuture<Response> responseFuture = new CompletableFuture<>();

    private Consumer<Response> successHandler;

    public InflightRequest(Request request, Consumer<Response> successHandler) {
      this.request = request;
      this.successHandler = successHandler;
    }

    public Request getRequest() {
      return request;
    }

    public boolean isConverted() {
      return converted;
    }

    public void setConverted(boolean converted) {
      this.converted = converted;
    }

    public Exception getConversionError() {
      return conversionError;
    }

    public void setConversionError(Exception conversionError) {
      this.conversionError = conversionError;
    }

    public Consumer<Response> getSuccessHandler() {
      return successHandler;
    }

    public CompletableFuture<Response> getResponseFuture() {
      return responseFuture;
    }

    public void resetResponse() {
      this.responseFuture = new CompletableFuture<>();
    }
  }
}
