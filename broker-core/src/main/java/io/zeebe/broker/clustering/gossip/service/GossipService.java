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
package io.zeebe.broker.clustering.gossip.service;

import java.io.File;

import io.zeebe.broker.clustering.gossip.Gossip;
import io.zeebe.broker.clustering.gossip.GossipContext;
import io.zeebe.broker.clustering.gossip.config.GossipConfiguration;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.LangUtil;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;

public class GossipService implements Service<Gossip>
{
    private final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();
    private final Injector<GossipContext> gossipContextInjector = new Injector<>();

    private Gossip gossip;
    private GossipContext gossipContext;
    private ActorReference actorRef;

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();
        this.gossipContext = gossipContextInjector.getValue();
        startContext.run(() ->
        {
            //create a gossip folder
            final GossipConfiguration configuration = gossipContext.getConfig();
            createFile(configuration.fileName());

            this.gossip = new Gossip(gossipContext);
            gossip.open();
            actorRef = actorScheduler.schedule(gossip);
        });
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        actorRef.close();
        gossip.close();
    }

    @Override
    public Gossip get()
    {
        return gossip;
    }

    public Injector<GossipContext> getGossipContextInjector()
    {
        return gossipContextInjector;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    private void createFile(String file)
    {
        final File f = new File(file);
        if (!f.exists())
        {
            try
            {
                f.getParentFile().mkdirs();
                f.createNewFile();
            }
            catch (Exception e)
            {
                LangUtil.rethrowUnchecked(e);
            }
        }
    }
}
