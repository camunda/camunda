package org.camunda.tngp.client.task.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.camunda.tngp.client.ClientProperties;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.task.Task;
import org.camunda.tngp.client.task.TaskHandler;
import org.camunda.tngp.client.task.TaskSubscription;
import org.camunda.tngp.client.util.ClientRule;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.test.broker.protocol.brokerapi.ControlMessageRequest;
import org.camunda.tngp.test.broker.protocol.brokerapi.StubBrokerRule;
import org.camunda.tngp.test.util.TestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TaskSubscriptionTest
{

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule broker = new StubBrokerRule();

    protected final Object monitor = new Object();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(broker).around(clientRule);

    protected TngpClient client;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
    }

    @After
    public void after()
    {
        continueTaskHandlingThreads();
    }

    @Test
    public void shouldCloseSubscriptionOnChannelClose()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);

        final TaskSubscription subscription = clientRule.taskTopic().newTaskSubscription()
            .handler((t) -> t.complete())
            .lockOwner("owner")
            .lockTime(1000L)
            .taskFetchSize(5)
            .taskType("foo")
            .open();

        // when
        broker.closeServerSocketBinding();

        // then
        TestUtil.waitUntil(() -> subscription.isClosed());
        assertThat(subscription.isClosed()).isTrue();
    }

    protected void continueTaskHandlingThreads()
    {
        synchronized (monitor)
        {
            monitor.notifyAll();
        }
    }

    /**
     * This tests a case that should not occur under normal circumstances, but might occur
     * in case of inconsistencies between broker and client state (e.g. due to bugs in either of them)
     */
    @Test
    public void shouldRetryWithMoreTasksThanSubscriptionCapacity() throws InterruptedException
    {
        // given
        broker.stubTaskSubscriptionApi(123L);
        broker.onExecuteCommandRequest((r) ->
                r.eventType() == EventType.TASK_EVENT && "COMPLETE".equals(r.getCommand().get("eventType")))
            .respondWith()
            .key((r) -> r.key())
            .topicName((r) -> r.topicName())
            .partitionId((r) -> r.partitionId())
            .event()
                .allOf((r) -> r.getCommand())
                .put("eventType", "COMPLETED")
                .done()
            .register();

        final WaitingTaskHandler handler = new WaitingTaskHandler();
        final Properties clientProperties = ((TngpClientImpl) client).getInitializationProperties();
        final int numExecutionThreads = Integer.parseInt(clientProperties.getProperty(ClientProperties.CLIENT_TASK_EXECUTION_THREADS));
        final int taskCapacity = 4;

        clientRule.taskTopic().newTaskSubscription()
            .handler(handler)
            .lockOwner("owner")
            .lockTime(1000L)
            .taskFetchSize(taskCapacity)
            .taskType("foo")
            .open();

        final int clientChannelId = broker.getReceivedControlMessageRequests().get(0).getChannelId();

        for (int i = 0; i < taskCapacity + numExecutionThreads; i++)
        {
            broker.pushLockedTask(clientChannelId, 123L, i, i, "foo");
        }

        TestUtil.waitUntil(() -> handler.numWaitingThreads.get() > 0);

        // pushing one more event, exceeding client capacity
        broker.pushLockedTask(clientChannelId, 123L, Integer.MAX_VALUE, Integer.MAX_VALUE, "foo");

        // waiting for the client to receive all pending tasks
        Thread.sleep(500L);

        // when
        handler.shouldWait = false;
        continueTaskHandlingThreads();

        // then the additional event is handled nevertheless (i.e. client applies backpressure)
        TestUtil.waitUntil(() -> handler.numHandledEvents.get() == taskCapacity + numExecutionThreads + 1);
    }

    /**
     * i.e. if signalling task failure itself fails
     */
    @Test
    public void shouldNotLoseCreditsOnFailureToReportTaskFailure() throws InterruptedException
    {
        // given
        broker.stubTaskSubscriptionApi(123L);
        failTaskFailure();

        final int subscriptionCapacity = 8;
        final AtomicInteger failedTasks = new AtomicInteger(0);

        final TaskHandler taskHandler = (t) ->
        {
            failedTasks.incrementAndGet();
            throw new RuntimeException("foo");
        };

        clientRule.taskTopic().newTaskSubscription()
            .handler(taskHandler)
            .lockOwner("owner")
            .lockTime(1000L)
            .taskFetchSize(subscriptionCapacity)
            .taskType("foo")
            .open();

        final int clientChannelId = broker.getReceivedControlMessageRequests().get(0).getChannelId();

        for (int i = 0; i < subscriptionCapacity; i++)
        {
            broker.pushLockedTask(clientChannelId, 123L, i, i, "foo");
        }


        // when
        TestUtil.waitUntil(() -> failedTasks.get() == 8);
        // give the client a bit of time to submit credits; this is not coupled to any defined event, so we just sleep for a bit
        Thread.sleep(500L);

        // then
        final List<ControlMessageRequest> creditRequests = broker.getReceivedControlMessageRequests().stream()
            .filter((r) -> r.messageType() == ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS)
            .collect(Collectors.toList());

        assertThat(creditRequests).isNotEmpty();
        final int numSubmittedCredits = creditRequests.stream().mapToInt((r) -> (int) r.getData().get("credits")).sum();
        assertThat(numSubmittedCredits).isGreaterThan(0);
    }

    protected void failTaskFailure()
    {
        broker.onExecuteCommandRequest((r) ->
                r.eventType() == EventType.TASK_EVENT && "FAIL".equals(r.getCommand().get("eventType")))
            .respondWithError()
            .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
            .errorData("failed to fail task")
            .register();
    }

    protected class WaitingTaskHandler implements TaskHandler
    {
        protected AtomicInteger numHandledEvents = new AtomicInteger(0);
        protected AtomicInteger numWaitingThreads = new AtomicInteger(0);
        protected boolean shouldWait = true;

        @Override
        public void handle(Task task)
        {
            try
            {
                if (shouldWait)
                {
                    synchronized (monitor)
                    {
                        numWaitingThreads.incrementAndGet();
                        monitor.wait();
                        numWaitingThreads.decrementAndGet();
                    }
                }

                numHandledEvents.incrementAndGet();
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }

    }
}
