package io.zeebe.servicecontainer;

public interface Service<S>
{
    void start(ServiceStartContext startContext);

    void stop(ServiceStopContext stopContext);

    S get();
}
