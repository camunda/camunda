/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.subscription;

import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ServerInputSubscription;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;

public class SubscriptionApiRequestHandlerService extends Actor
    implements Service<SubscriptionApiRequestHandler> {

  private final Injector<BufferingServerTransport> serverTransportInjector = new Injector<>();

  private BufferingServerTransport serverTransport;
  private SubscriptionApiRequestHandler requestHandler;

  @Override
  public String getName() {
    return "subscription-api";
  }

  @Override
  public void start(ServiceStartContext context) {
    serverTransport = serverTransportInjector.getValue();

    requestHandler = new SubscriptionApiRequestHandler();

    context.async(context.getScheduler().submitActor(this, true));
  }

  @Override
  protected void onActorStarting() {
    final ActorFuture<ServerInputSubscription> openFuture =
        serverTransport.openSubscription("subscriptionRequestHandler", requestHandler, null);

    actor.runOnCompletion(
        openFuture,
        (subscription, throwable) -> {
          if (throwable != null) {
            throw new RuntimeException(throwable);
          } else {
            actor.consume(
                subscription,
                () -> {
                  if (subscription.poll() == 0) {
                    actor.yield();
                  }
                });
          }
        });
  }

  @Override
  public SubscriptionApiRequestHandler get() {
    return requestHandler;
  }

  public Injector<BufferingServerTransport> getServerTransportInjector() {
    return serverTransportInjector;
  }
}
