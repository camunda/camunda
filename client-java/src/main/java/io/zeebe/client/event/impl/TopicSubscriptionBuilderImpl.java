package io.zeebe.client.event.impl;

import io.zeebe.client.event.IncidentEvent;
import io.zeebe.client.event.IncidentEventHandler;
import io.zeebe.client.event.RaftEvent;
import io.zeebe.client.event.RaftEventHandler;
import io.zeebe.client.event.TaskEventHandler;
import io.zeebe.client.event.TopicEventHandler;
import io.zeebe.client.event.TopicEventType;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.client.event.TopicSubscriptionBuilder;
import io.zeebe.client.event.WorkflowInstanceEventHandler;
import io.zeebe.client.impl.data.MsgPackMapper;
import io.zeebe.client.task.impl.subscription.EventAcquisition;
import io.zeebe.util.EnsureUtil;

public class TopicSubscriptionBuilderImpl implements TopicSubscriptionBuilder
{
    protected TopicEventHandler defaultEventHandler;
    protected TaskEventHandler taskEventHandler;
    protected WorkflowInstanceEventHandler wfEventHandler;
    protected IncidentEventHandler incidentEventHandler;
    protected RaftEventHandler raftEventHandler;

    protected final TopicSubscriptionImplBuilder builder;
    protected final MsgPackMapper msgPackMapper;

    public TopicSubscriptionBuilderImpl(
            TopicClientImpl client,
            EventAcquisition<TopicSubscriptionImpl> acquisition,
            MsgPackMapper msgPackMapper,
            int prefetchCapacity)
    {
        builder = new TopicSubscriptionImplBuilder(client, acquisition, prefetchCapacity);
        this.msgPackMapper = msgPackMapper;
    }

    @Override
    public TopicSubscriptionBuilder handler(TopicEventHandler handler)
    {
        this.defaultEventHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilder taskEventHandler(TaskEventHandler handler)
    {
        this.taskEventHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilder workflowInstanceEventHandler(WorkflowInstanceEventHandler handler)
    {
        this.wfEventHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilder incidentEventHandler(IncidentEventHandler handler)
    {
        this.incidentEventHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilderImpl raftEventHandler(final RaftEventHandler raftEventHandler)
    {
        this.raftEventHandler = raftEventHandler;
        return this;
    }

    @Override
    public TopicSubscription open()
    {
        EnsureUtil.ensureNotNull("name", builder.getName());
        if (defaultEventHandler == null && taskEventHandler == null && wfEventHandler == null && incidentEventHandler == null && raftEventHandler == null)
        {
            throw new RuntimeException("at least one handler must be set");
        }

        builder.handler(this::dispatchEvent);

        final TopicSubscriptionImpl subscription = builder.build();
        subscription.open();
        return subscription;
    }

    protected void dispatchEvent(TopicEventImpl event) throws Exception
    {
        final TopicEventType eventType = event.getEventType();

        if (TopicEventType.TASK == eventType && taskEventHandler != null)
        {
            final TaskEventImpl taskEvent = msgPackMapper.convert(event.getAsMsgPack(), TaskEventImpl.class);
            taskEventHandler.handle(event, taskEvent);
        }
        else if (TopicEventType.WORKFLOW_INSTANCE == eventType && wfEventHandler != null)
        {
            final WorkflowInstanceEventImpl wfEvent = msgPackMapper.convert(event.getAsMsgPack(), WorkflowInstanceEventImpl.class);
            wfEventHandler.handle(event, wfEvent);
        }
        else if (TopicEventType.INCIDENT == eventType && incidentEventHandler != null)
        {
            final IncidentEvent incidentEvent = msgPackMapper.convert(event.getAsMsgPack(), IncidentEventImpl.class);
            incidentEventHandler.handle(event, incidentEvent);
        }
        else if (TopicEventType.RAFT == eventType && raftEventHandler != null)
        {
            final RaftEvent raftEvent = msgPackMapper.convert(event.getAsMsgPack(), RaftEventImpl.class);
            raftEventHandler.handle(event, raftEvent);
        }
        else if (defaultEventHandler != null)
        {
            defaultEventHandler.handle(event, event);
        }
    }

    @Override
    public TopicSubscriptionBuilder startAtPosition(long position)
    {
        builder.startPosition(position);
        return this;
    }

    @Override
    public TopicSubscriptionBuilder startAtTailOfTopic()
    {
        builder.startAtTailOfTopic();
        return this;
    }

    @Override
    public TopicSubscriptionBuilder startAtHeadOfTopic()
    {
        builder.startAtHeadOfTopic();
        return this;
    }

    @Override
    public TopicSubscriptionBuilder name(String name)
    {
        builder.name(name);
        return this;
    }

    @Override
    public TopicSubscriptionBuilder forcedStart()
    {
        builder.forceStart();
        return this;
    }
}
