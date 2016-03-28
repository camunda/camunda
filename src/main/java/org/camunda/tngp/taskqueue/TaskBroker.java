package org.camunda.tngp.taskqueue;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.Logs;
import org.camunda.tngp.taskqueue.protocol.CreateTaskInstanceHandler;
import org.camunda.tngp.taskqueue.protocol.SbeRequestDispatcher;
import org.camunda.tngp.transport.ReceiveBufferChannelHandler;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.camunda.tngp.transport.requestresponse.server.AsyncWorker;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;
import org.camunda.tngp.transport.Transports;

import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.BackoffIdleStrategy;

public class TaskBroker
{
    private static final int port = 8080;

    public static void main(String[] args) throws InterruptedException
    {
        Log log = Logs.createLog("topic1")
                .logRootPath("/tmp/task-queue-logs/")
                .logSegmentSize(1024 * 1024 * 512)
                .writeBufferBuilder(Dispatchers.create("log-write-buffer")
                        .bufferSize(1024 * 1024 * 16)
                        .subscriberGroups(2))
                .build();

        log.start();

        final Transport transport = Transports.createTransport("task-broker-transport")
                .build();

        final Dispatcher workerRequestBuffer = Dispatchers.create("woker-request-buffer")
                .bufferSize(1024*1024*16)
                .buildAndStart();

        final TaskQueueContext taskQueueContext = new TaskQueueContext();
        taskQueueContext.setLog(log);
        taskQueueContext.setRequestBuffer(workerRequestBuffer);
        taskQueueContext.setResponseBuffer(transport.getSendBuffer());
        taskQueueContext.setResponsePool(new DeferredResponsePool(transport.getSendBuffer(), 128));

        SbeRequestDispatcher sbeRequestDispatcher = new SbeRequestDispatcher();
        sbeRequestDispatcher.reqisterHandler(new CreateTaskInstanceHandler(taskQueueContext));
        taskQueueContext.setRequestHandler(sbeRequestDispatcher);

        final AsyncWorker taskQueueWorker = new AsyncWorker("task-queue-worker", taskQueueContext);

        BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy(100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MILLISECONDS.toNanos(100));
        AgentRunner agentRunner = new AgentRunner(idleStrategy, (t)-> t.printStackTrace(), null, taskQueueWorker);
        AgentRunner.startOnThread(agentRunner);

        transport.createServerSocketBinding(new InetSocketAddress("localhost", port))
                .transportChannelHandler(new ReceiveBufferChannelHandler(workerRequestBuffer))
                .bind();

        System.out.println("Server socket bound to port " + 8080);
    }

}
