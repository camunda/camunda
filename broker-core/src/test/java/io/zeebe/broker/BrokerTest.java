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
package io.zeebe.broker;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import io.zeebe.util.sched.future.ActorFuture;
import org.junit.After;
import org.junit.Test;
import io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.broker.system.ConfigurationManagerImpl;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;

public class BrokerTest
{

    protected Broker broker;

    @After
    public void tearDown()
    {
        if (broker != null)
        {
            broker.close();
        }
    }

    /**
     * This tests the constructor that takes a {@link ConfigurationManager} instance.
     * This is used in the Spring integration.
     */
    @Test
    public void shouldCreateBrokerWithConfigurationManager()
    {
        final InputStream configStream = BrokerTest.class.getClassLoader().getResourceAsStream("zeebe.unit-test.cfg.toml");
        final ConfigurationManager configurationManager = new ConfigurationManagerImpl(configStream);

        // when
        broker = new Broker(configurationManager);

        // then I can register a dependency to a broker service successfully
        final ActorFuture<Object> future = broker.getBrokerContext()
            .getServiceContainer()
            .createService(ServiceName.newServiceName("foo", Object.class), new Service<Object>()
            {
                @Override
                public Object get()
                {
                    return null;
                }
            })
            .dependency(ClusterBaseLayerServiceNames.GOSSIP_SERVICE)
            .install();

        waitUntil(future::isDone);
        assertThat(future).isDone();
    }
}
