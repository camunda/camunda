package io.zeebe.broker.it;

import static io.zeebe.broker.task.TaskQueueServiceNames.taskQueueInstanceStreamProcessorServiceName;
import static io.zeebe.logstreams.log.LogStream.DEFAULT_LOG_NAME;

import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.zeebe.broker.Broker;
import io.zeebe.broker.event.TopicSubscriptionServiceNames;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.servicecontainer.*;
import org.junit.rules.ExternalResource;

public class EmbeddedBrokerRule extends ExternalResource
{
    protected Broker broker;

    protected Supplier<InputStream> configSupplier;

    public EmbeddedBrokerRule()
    {
        this(() -> null);
    }

    public EmbeddedBrokerRule(Supplier<InputStream> configSupplier)
    {
        this.configSupplier = configSupplier;
    }

    @Override
    protected void before() throws Throwable
    {
        startBroker();
    }

    @Override
    protected void after()
    {
        stopBroker();
    }

    public void restartBroker()
    {
        stopBroker();
        startBroker();
    }

    public void stopBroker()
    {
        broker.close();
    }

    public void startBroker()
    {
        broker = new Broker(configSupplier.get());

        final ServiceContainer serviceContainer = broker.getBrokerContext().getServiceContainer();

        try
        {
            // Hack: block until default task queue log has been installed
            // How to make it better: https://github.com/camunda-tngp/zeebe/issues/196
            serviceContainer.createService(TestService.NAME, new TestService())
                .dependency(TopicSubscriptionServiceNames.subscriptionManagementServiceName(DEFAULT_LOG_NAME))
                .dependency(TransportServiceNames.serverTransport(TransportServiceNames.CLIENT_API_SERVER_NAME))
                .dependency(taskQueueInstanceStreamProcessorServiceName(DEFAULT_LOG_NAME))
                .install()
                .get(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException | ExecutionException | TimeoutException e)
        {
            stopBroker();
            throw new RuntimeException("Default task queue log not installed into the container withing 5 seconds.");
        }
    }

    static class TestService implements Service<TestService>
    {

        static final ServiceName<TestService> NAME = ServiceName.newServiceName("testService", TestService.class);

        @Override
        public void start(ServiceStartContext startContext)
        {

        }

        @Override
        public void stop(ServiceStopContext stopContext)
        {

        }

        @Override
        public TestService get()
        {
            return this;
        }

    }

}
