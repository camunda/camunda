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
package io.camunda.zeebe.client.impl.command;

import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.command.StreamJobsCommandStep1;
import io.camunda.zeebe.client.api.command.StreamJobsCommandStep1.StreamJobsCommandStep2;
import io.camunda.zeebe.client.api.command.StreamJobsCommandStep1.StreamJobsCommandStep3;
import io.camunda.zeebe.client.impl.response.ActivatedJobImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamJobsControl;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import net.jcip.annotations.ThreadSafe;

public final class StreamJobsCommandImpl
    implements StreamJobsCommandStep1, StreamJobsCommandStep2, StreamJobsCommandStep3 {

  private final GatewayStub asyncStub;
  private final JsonMapper jsonMapper;
  private final Predicate<Throwable> retryPredicate;
  private final StreamJobsControl.Registration.Builder builder;

  private StreamJobsListener listener;
  private Duration requestTimeout;

  private final Set<String> defaultTenantIds;
  private final Set<String> customTenantIds;

  public StreamJobsCommandImpl(
      final GatewayStub asyncStub,
      final JsonMapper jsonMapper,
      final Predicate<Throwable> retryPredicate,
      final ZeebeClientConfiguration config) {
    this.asyncStub = asyncStub;
    this.jsonMapper = jsonMapper;
    this.retryPredicate = retryPredicate;
    builder = StreamJobsControl.Registration.newBuilder();

    timeout(config.getDefaultJobTimeout());
    workerName(config.getDefaultJobWorkerName());

    defaultTenantIds = new HashSet<>(config.getDefaultJobWorkerTenantIds());
    customTenantIds = new HashSet<>();
  }

  @Override
  public StreamJobsCommandStep2 jobType(final String jobType) {
    builder.setType(Objects.requireNonNull(jobType, "must specify a job type"));
    return this;
  }

  @Override
  public StreamJobsCommandStep3 listener(final StreamJobsListener listener) {
    this.listener = Objects.requireNonNull(listener, "must specify a job listener");
    return this;
  }

  @Override
  public StreamJobsCommandStep3 timeout(final Duration timeout) {
    Objects.requireNonNull(timeout, "must specify a job timeout");
    builder.setTimeout(timeout.toMillis());
    return this;
  }

  @Override
  public StreamJobsCommandStep3 workerName(final String workerName) {
    builder.setWorker(workerName);
    return this;
  }

  @Override
  public StreamJobsCommandStep3 fetchVariables(final List<String> fetchVariables) {
    builder.addAllFetchVariable(fetchVariables);
    return this;
  }

  @Override
  public StreamJobsCommandStep3 fetchVariables(final String... fetchVariables) {
    return fetchVariables(Arrays.asList(fetchVariables));
  }

  @Override
  public StreamJobsCommandStep3 requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public JobStream open() {
    // because we use `withWaitForReady`, we can send the initial registration request immediately
    // it will simply be queued until the stream is connected and ready
    GatewayStub stub = asyncStub.withWaitForReady();

    if (requestTimeout != null) {
      stub = stub.withDeadlineAfter(requestTimeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    if (customTenantIds.isEmpty()) {
      builder.addAllTenantIds(defaultTenantIds);
    } else {
      builder.addAllTenantIds(customTenantIds);
    }

    final StreamJobsControl registrationRequest =
        StreamJobsControl.newBuilder().setRegistration(builder.build()).build();

    final JobStreamImpl controller =
        new JobStreamImpl(listener, jsonMapper, retryPredicate, stub, registrationRequest);
    controller.open();
    return controller;
  }

  @Override
  public StreamJobsCommandStep3 tenantId(final String tenantId) {
    customTenantIds.add(tenantId);
    return this;
  }

  @Override
  public StreamJobsCommandStep3 tenantIds(final List<String> tenantIds) {
    customTenantIds.clear();
    customTenantIds.addAll(tenantIds);
    return this;
  }

  @Override
  public StreamJobsCommandStep3 tenantIds(final String... tenantIds) {
    return tenantIds(Arrays.asList(tenantIds));
  }

  @ThreadSafe
  private static final class JobStreamImpl
      implements JobStream, StreamObserver<GatewayOuterClass.ActivatedJob> {

    private final AtomicBoolean closed = new AtomicBoolean();
    private final StreamJobsListener listener;
    private final JsonMapper jsonMapper;
    private final Predicate<Throwable> retryPredicate;
    private final GatewayStub gatewayStub;
    private final StreamJobsControl registrationRequest;

    private volatile StreamObserver<StreamJobsControl> requestStream;

    private JobStreamImpl(
        final StreamJobsListener listener,
        final JsonMapper jsonMapper,
        final Predicate<Throwable> retryPredicate,
        final GatewayStub gatewayStub,
        final StreamJobsControl registrationRequest) {
      this.listener = listener;
      this.jsonMapper = jsonMapper;
      this.retryPredicate = retryPredicate;
      this.gatewayStub = gatewayStub;
      this.registrationRequest = registrationRequest;
    }

    @Override
    public void onNext(final GatewayOuterClass.ActivatedJob value) {
      try {
        final ActivatedJobImpl mappedJob = new ActivatedJobImpl(jsonMapper, value);
        listener.onJob(mappedJob);
      } catch (final Exception e) {
        onError(e);
      }
    }

    @Override
    public void onError(final Throwable t) {
      if (retryPredicate.test(t)) {
        open();
      } else {
        closed.set(true);
        listener.onError(t);
      }
    }

    @Override
    public void onCompleted() {
      // TODO: do we want to differentiate between client-side close (i.e. the close() method) and
      // server-side closed (i.e. this method)
      close();
    }

    @Override
    public void close() {
      if (!closed.compareAndSet(false, true)) {
        return;
      }

      if (requestStream != null) {
        try {
          requestStream.onCompleted();
        } catch (final Exception ignored) {
          // it could be that the stream is already closed, which would throw, but in this case we
          // can simply ignore it
        }
      }

      listener.onClose();
    }

    private void open() {
      if (closed.get()) {
        return;
      }

      requestStream = gatewayStub.streamJobs(this);
      if (closed.get()) {
        requestStream.onCompleted();
      } else {
        requestStream.onNext(registrationRequest);
      }
    }
  }
}
