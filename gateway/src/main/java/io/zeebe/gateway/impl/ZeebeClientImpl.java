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
package io.zeebe.gateway.impl;

import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.ZeebeClientConfiguration;
import io.zeebe.gateway.api.clients.JobClient;
import io.zeebe.gateway.api.clients.WorkflowClient;
import io.zeebe.gateway.api.commands.TopologyRequestStep1;
import io.zeebe.gateway.api.record.ZeebeObjectMapper;
import io.zeebe.gateway.api.subscription.TopicSubscriptionBuilderStep1;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.clustering.TopologyRequestImpl;
import io.zeebe.gateway.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.gateway.impl.subscription.SubscriptionManager;
import io.zeebe.gateway.impl.subscription.topic.TopicSubscriptionBuilderImpl;
import io.zeebe.util.sched.clock.ActorClock;

// TODO: remove with https://github.com/zeebe-io/zeebe/issues/1377
public class ZeebeClientImpl extends BrokerClient implements ZeebeClient {

  protected final ZeebeObjectMapperImpl objectMapper;

  protected final RequestManager apiCommandManager;
  protected final SubscriptionManager subscriptionManager;

  public ZeebeClientImpl(final ZeebeClientConfiguration configuration) {
    this(configuration, null);
  }

  public ZeebeClientImpl(
      final ZeebeClientConfiguration configuration, final ActorClock actorClock) {
    super(configuration, actorClock);

    this.objectMapper = new ZeebeObjectMapperImpl();

    final long requestBlockTimeMs = configuration.getRequestBlocktime().toMillis();
    apiCommandManager =
        new RequestManager(
            transport.getOutput(),
            topologyManager,
            objectMapper,
            configuration.getRequestTimeout(),
            requestBlockTimeMs);
    actorScheduler.submitActor(apiCommandManager);

    this.subscriptionManager = new SubscriptionManager(this);
    this.transport.registerChannelListener(subscriptionManager);
    actorScheduler.submitActor(subscriptionManager);
  }

  @Override
  public void close() {
    if (isClosed) {
      return;
    }

    doAndLogException(() -> subscriptionManager.close().join());
    LOG.debug("subscriber group manager closed");
    doAndLogException(() -> apiCommandManager.close().join());
    LOG.debug("api command manager closed");

    super.close();
  }

  protected void doAndLogException(final Runnable r) {
    try {
      r.run();
    } catch (final Exception e) {
      Loggers.BROKER_CLIENT_LOGGER.error("Exception when closing client. Ignoring", e);
    }
  }

  public RequestManager getCommandManager() {
    return apiCommandManager;
  }

  public ZeebeObjectMapperImpl getObjectMapper() {
    return objectMapper;
  }

  public SubscriptionManager getSubscriptionManager() {
    return subscriptionManager;
  }

  @Override
  public WorkflowClient workflowClient() {
    return new WorkflowsClientImpl(this);
  }

  @Override
  public JobClient jobClient() {
    return new JobClientImpl(this);
  }

  @Override
  public TopicSubscriptionBuilderStep1 newSubscription() {
    return new TopicSubscriptionBuilderImpl(this);
  }

  @Override
  public ZeebeObjectMapper objectMapper() {
    return objectMapper;
  }

  @Override
  public TopologyRequestStep1 newTopologyRequest() {
    return new TopologyRequestImpl(this);
  }
}
