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
package io.zeebe.broker.system.management.topics;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.orchestration.state.KnownTopics;
import io.zeebe.broker.system.management.LeaderManagementRequestHandler;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;

public class FetchCreatedTopicsRequestHandlerService
    implements Service<FetchCreatedTopicsRequestHandlerService> {

  private final ServiceGroupReference<Partition> partitionsGroupReference =
      ServiceGroupReference.<Partition>create()
          .onAdd((name, partition) -> installServices())
          .build();

  private final Injector<LeaderManagementRequestHandler> requestHandlerServiceInjector =
      new Injector<>();

  private final Injector<KnownTopics> knownTopicsInjector = new Injector<>();

  private void installServices() {
    final LeaderManagementRequestHandler requestHandlerService =
        getRequestHandlerServiceInjector().getValue();

    final KnownTopics knownTopics = getKnownTopicsInjector().getValue();

    final FetchCreatedTopicsRequestHandler handler =
        new FetchCreatedTopicsRequestHandler(knownTopics);

    requestHandlerService.setFetchCreatedTopicsRequestHandler(handler);
  }

  @Override
  public FetchCreatedTopicsRequestHandlerService get() {
    return this;
  }

  public ServiceGroupReference<Partition> getPartitionsGroupReference() {
    return partitionsGroupReference;
  }

  public Injector<LeaderManagementRequestHandler> getRequestHandlerServiceInjector() {
    return requestHandlerServiceInjector;
  }

  public Injector<KnownTopics> getKnownTopicsInjector() {
    return knownTopicsInjector;
  }
}
