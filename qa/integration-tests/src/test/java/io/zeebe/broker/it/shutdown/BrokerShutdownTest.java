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
package io.zeebe.broker.it.shutdown;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.broker.transport.controlmessage.AbstractControlMessageHandler;
import io.zeebe.broker.transport.controlmessage.ControlMessageHandlerManager;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.api.commands.Workflows;
import io.zeebe.client.impl.events.WorkflowImpl;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.impl.data.repository.ListWorkflowsResponse;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.ServerSocketBinding;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CyclicBarrier;
import org.agrona.DirectBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class BrokerShutdownTest {

  private static final ServiceName<Void> BLOCK_BROKER_SERVICE_NAME =
      ServiceName.newServiceName("blockService", Void.class);
  private static final ServiceName<ControllableListWorkflowsMessageHandler>
      CONTROLLABLE_MESSAGE_HANDLER_SERVICE_NAME =
          ServiceName.newServiceName(
              "controllableMessageHandlerService", ControllableListWorkflowsMessageHandler.class);

  private static final Workflow EXPECTED_WORKFLOW =
      new WorkflowImpl(123, "test-process", 34, "process.bpmn");

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public Timeout timeout = Timeout.seconds(60);

  @Test
  public void shouldCompleteRequestBeforeShutdown() {
    // given
    final ControllableListWorkflowsMessageHandlerService service =
        new ControllableListWorkflowsMessageHandlerService();
    brokerRule.installService(
        serviceContainer ->
            serviceContainer
                .createService(CONTROLLABLE_MESSAGE_HANDLER_SERVICE_NAME, service)
                .dependency(
                    TransportServiceNames.serverTransport(
                        TransportServiceNames.CLIENT_API_SERVER_NAME),
                    service.getServerTransportInjector())
                .dependency(
                    TransportServiceNames.CONTROL_MESSAGE_HANDLER_MANAGER,
                    service.getHandlerManagerInjector()));

    final ZeebeFuture<Workflows> future = clientRule.getClient().newWorkflowRequest().send();
    service.get().await();

    // when
    brokerRule.stopBroker();

    // then
    assertThat(future.join().getWorkflows()).containsExactly(EXPECTED_WORKFLOW);
  }

  @Test
  public void shouldReleaseSockets() {
    // given
    brokerRule.installService(
        serviceContainer ->
            serviceContainer
                .createService(BLOCK_BROKER_SERVICE_NAME, new BlockingService())
                .dependency(
                    TransportServiceNames.bufferingServerTransport(
                        TransportServiceNames.MANAGEMENT_API_SERVER_NAME))
                .dependency(
                    TransportServiceNames.serverTransport(
                        TransportServiceNames.CLIENT_API_SERVER_NAME))
                .dependency(
                    TransportServiceNames.serverTransport(
                        TransportServiceNames.REPLICATION_API_SERVER_NAME)));

    final Broker broker = brokerRule.getBroker();
    broker.getBrokerContext().setCloseTimeout(Duration.ofSeconds(1));

    // when
    broker.close();

    // then
    final NetworkCfg networkCfg = broker.getBrokerContext().getBrokerConfiguration().getNetwork();

    tryToBindSocketAddress(networkCfg.getManagement().toSocketAddress());
    tryToBindSocketAddress(networkCfg.getReplication().toSocketAddress());
    tryToBindSocketAddress(networkCfg.getClient().toSocketAddress());
  }

  private void tryToBindSocketAddress(SocketAddress socketAddress) {
    final InetSocketAddress socket = socketAddress.toInetSocketAddress();
    final ServerSocketBinding binding = new ServerSocketBinding(socket);
    binding.doBind();
    binding.close();
  }

  private class BlockingService implements Service<Void> {
    @Override
    public void start(ServiceStartContext startContext) {}

    @Override
    public void stop(ServiceStopContext stopContext) {
      final CompletableActorFuture<Void> neverCompletingFuture = new CompletableActorFuture<>();
      stopContext.async(neverCompletingFuture);
    }

    @Override
    public Void get() {
      return null;
    }
  }

  class ControllableListWorkflowsMessageHandlerService
      implements Service<ControllableListWorkflowsMessageHandler> {

    private Injector<ControlMessageHandlerManager> handlerManagerInjector = new Injector<>();
    private Injector<ServerTransport> serverTransportInjector = new Injector<>();
    private ControllableListWorkflowsMessageHandler handler;

    @Override
    public void start(ServiceStartContext startContext) {
      handler =
          new ControllableListWorkflowsMessageHandler(
              serverTransportInjector.getValue().getOutput());
      handlerManagerInjector.getValue().registerHandler(handler);
    }

    @Override
    public ControllableListWorkflowsMessageHandler get() {
      return handler;
    }

    Injector<ControlMessageHandlerManager> getHandlerManagerInjector() {
      return handlerManagerInjector;
    }

    Injector<ServerTransport> getServerTransportInjector() {
      return serverTransportInjector;
    }
  }

  class ControllableListWorkflowsMessageHandler extends AbstractControlMessageHandler {

    private CyclicBarrier barrier = new CyclicBarrier(2);

    ControllableListWorkflowsMessageHandler(ServerOutput output) {
      super(output);
    }

    @Override
    public ControlMessageType getMessageType() {
      return ControlMessageType.LIST_WORKFLOWS;
    }

    @Override
    public void handle(
        ActorControl actor,
        int partitionId,
        DirectBuffer requestBuffer,
        long requestId,
        int requestStreamId) {
      await();
      actor.runDelayed(
          Duration.ofMillis(250),
          () -> {
            final ListWorkflowsResponse response = new ListWorkflowsResponse();
            response
                .getWorkflows()
                .add()
                .setBpmnProcessId(EXPECTED_WORKFLOW.getBpmnProcessId())
                .setResourceName(EXPECTED_WORKFLOW.getResourceName())
                .setVersion(EXPECTED_WORKFLOW.getVersion())
                .setWorkflowKey(EXPECTED_WORKFLOW.getWorkflowKey());
            sendResponse(actor, requestStreamId, requestId, response);
          });
    }

    void await() {
      try {
        barrier.await();
      } catch (Exception e) {
        throw new RuntimeException("Failed to wait for barrier", e);
      }
    }
  }
}
