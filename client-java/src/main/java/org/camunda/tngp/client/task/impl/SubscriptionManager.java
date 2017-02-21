package org.camunda.tngp.client.task.impl;

import java.util.concurrent.TimeUnit;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.CompositeAgent;
import org.camunda.tngp.client.AsyncTasksClient;
import org.camunda.tngp.client.event.PollableTopicSubscriptionBuilder;
import org.camunda.tngp.client.event.TaskTopicSubscriptionBuilder;
import org.camunda.tngp.client.event.TopicSubscriptionBuilder;
import org.camunda.tngp.client.event.impl.EventAcquisition;
import org.camunda.tngp.client.event.impl.PollableTopicSubscriptionBuilderImpl;
import org.camunda.tngp.client.event.impl.TaskSubscriptionLifecycle;
import org.camunda.tngp.client.event.impl.TaskTopicSubscriptionBuilderImpl;
import org.camunda.tngp.client.event.impl.TopicSubscriptionBuilderImpl;
import org.camunda.tngp.client.event.impl.TopicSubscriptionImpl;
import org.camunda.tngp.client.event.impl.TopicSubscriptionLifecycle;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.impl.data.MsgPackMapper;
import org.camunda.tngp.client.task.PollableTaskSubscriptionBuilder;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;
import org.camunda.tngp.dispatcher.Subscription;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SubscriptionManager
{

    protected final EventAcquisition<TaskSubscriptionImpl> taskAcqusition;
    protected final EventAcquisition<TopicSubscriptionImpl> topicSubscriptionAcquisition;
    protected final SubscribedEventCollector taskCollector;
    protected final AsyncTasksClient taskClient;
    protected final MsgPackMapper msgPackMapper;

    protected AgentRunner acquisitionRunner;
    protected AgentRunner[] executionRunners;
    protected final int numExecutionThreads;

    protected final EventSubscriptions<TaskSubscriptionImpl> taskSubscriptions;
    protected final EventSubscriptions<TopicSubscriptionImpl> topicSubscriptions;

    protected final boolean autoCompleteTasks;

    public SubscriptionManager(
            TngpClientImpl client,
            int numExecutionThreads,
            boolean autoCompleteTasks,
            Subscription receiveBufferSubscription)
    {
        this.taskClient = client;
        this.taskSubscriptions = new EventSubscriptions<>();
        this.topicSubscriptions = new EventSubscriptions<>();

        this.taskCollector = new SubscribedEventCollector(receiveBufferSubscription);
        this.taskAcqusition = new EventAcquisition<>(
                "task-acquisition", taskSubscriptions, new TaskSubscriptionLifecycle(client));
        this.topicSubscriptionAcquisition = new EventAcquisition<>(
                "topic-event-acquisition", topicSubscriptions, new TopicSubscriptionLifecycle(client));
        taskCollector.setTaskHandler(taskAcqusition);
        taskCollector.setTopicEventHandler(topicSubscriptionAcquisition);

        this.numExecutionThreads = numExecutionThreads;
        this.autoCompleteTasks = autoCompleteTasks;
        this.msgPackMapper = new MsgPackMapper(new ObjectMapper(new MessagePackFactory()));
    }

    public void start()
    {
        startAcquisition();
        startExecution();
    }

    public void stop()
    {
        stopAcquisition();
        stopExecution();
    }

    protected void startAcquisition()
    {
        if (acquisitionRunner == null)
        {
            acquisitionRunner = newAgentRunner(new CompositeAgent(taskCollector, taskAcqusition, topicSubscriptionAcquisition));
        }

        AgentRunner.startOnThread(acquisitionRunner);
    }

    protected void stopAcquisition()
    {
        acquisitionRunner.close();
        acquisitionRunner = null;
    }

    protected void startExecution()
    {
        if (executionRunners == null)
        {
            executionRunners = new AgentRunner[numExecutionThreads];
            for (int i = 0; i < executionRunners.length; i++)
            {
                executionRunners[i] = newAgentRunner(new CompositeAgent(new SubscriptionExecutor(taskSubscriptions), new SubscriptionExecutor(topicSubscriptions)));
            }
        }

        for (int i = 0; i < executionRunners.length; i++)
        {
            AgentRunner.startOnThread(executionRunners[i]);
        }
    }

    protected void stopExecution()
    {
        for (int i = 0; i < executionRunners.length; i++)
        {
            executionRunners[i].close();
        }
        executionRunners = null;
    }

    protected static AgentRunner newAgentRunner(Agent agent)
    {
        return new AgentRunner(
            new BackoffIdleStrategy(1000, 100, 100, TimeUnit.MILLISECONDS.toNanos(10)),
            (e) -> e.printStackTrace(),
            null,
            agent);
    }

    public void closeAllSubscriptions()
    {
        this.taskSubscriptions.closeAll();
        this.topicSubscriptions.closeAll();
    }

    public TaskSubscriptionBuilder newTaskSubscription()
    {
        return new TaskSubscriptionBuilderImpl(taskClient, taskAcqusition, autoCompleteTasks, msgPackMapper);
    }

    public PollableTaskSubscriptionBuilder newPollableTaskSubscription()
    {
        return new PollableTaskSubscriptionBuilderImpl(taskClient, taskAcqusition, autoCompleteTasks, msgPackMapper);
    }

    public TopicSubscriptionBuilder newTopicSubscription(int topicId)
    {
        return new TopicSubscriptionBuilderImpl(topicId, topicSubscriptionAcquisition);
    }

    public PollableTopicSubscriptionBuilder newPollableTopicSubscription(int topicId)
    {
        return new PollableTopicSubscriptionBuilderImpl(topicId, topicSubscriptionAcquisition);
    }

    public TaskTopicSubscriptionBuilder newTaskTopicSubscription(int topicId)
    {
        return new TaskTopicSubscriptionBuilderImpl(topicId, topicSubscriptionAcquisition, msgPackMapper);
    }

}
