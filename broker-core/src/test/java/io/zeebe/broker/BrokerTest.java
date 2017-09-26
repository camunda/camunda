package io.zeebe.broker;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import org.junit.Test;

import io.zeebe.broker.clustering.ClusterServiceNames;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.broker.system.ConfigurationManagerImpl;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class BrokerTest
{

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
        final Broker broker = new Broker(configurationManager);

        // then I can register a dependency to a broker service successfully
        final CompletableFuture<Void> future = broker.getBrokerContext().getServiceContainer()
            .createService(ServiceName.newServiceName("foo", Object.class), new Service<Object>()
            {
                @Override
                public void start(ServiceStartContext startContext)
                {
                }

                @Override
                public void stop(ServiceStopContext stopContext)
                {
                }

                @Override
                public Object get()
                {
                    return null;
                }
            })
            .dependency(ClusterServiceNames.CLUSTER_MANAGER_SERVICE)
            .install();

        waitUntil(() -> future.isDone());
        assertThat(future).isCompleted();
    }
}
