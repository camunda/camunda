package org.camunda.tngp.client.event;

import java.util.function.BiFunction;

public class FailingHandler extends RecordingEventHandler
{

    protected BiFunction<EventMetadata, TopicEvent, Boolean> failureCondition;

    public FailingHandler(BiFunction<EventMetadata, TopicEvent, Boolean> failureCondition)
    {
        this.failureCondition = failureCondition;
    }

    public FailingHandler()
    {
        this ((m, e) -> true);
    }

    @Override
    public void handle(EventMetadata metadata, TopicEvent event)
    {
        super.handle(metadata, event);

        if (failureCondition.apply(metadata, event))
        {
            throw new RuntimeException("Handler invocation fails");
        }
    }
}
