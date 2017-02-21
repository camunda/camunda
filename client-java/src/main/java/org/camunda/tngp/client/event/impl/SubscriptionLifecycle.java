package org.camunda.tngp.client.event.impl;

public interface SubscriptionLifecycle<T extends EventSubscription<T>>
{

    Long requestNewSubscription(T subscription);

    void requestSubscriptionClose(T subscription);

    void onEventsPolled(T subscription, int numEvents);

}
