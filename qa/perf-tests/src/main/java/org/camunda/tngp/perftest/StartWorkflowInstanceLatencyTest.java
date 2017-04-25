package org.camunda.tngp.perftest;

import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;
import static org.camunda.tngp.perftest.CommonProperties.DEFAULT_PARTITION_ID;
import static org.camunda.tngp.perftest.CommonProperties.DEFAULT_TOPIC_NAME;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.WorkflowTopicClient;
import org.camunda.tngp.perftest.helper.FixedRateLatencyTest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;

public class StartWorkflowInstanceLatencyTest extends FixedRateLatencyTest
{
    @Override
    protected void executeSetup(Properties properties, TngpClient client)
    {
        final WorkflowTopicClient workflowsClient = client.workflowTopic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);

        final BpmnModelInstance processModel = Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("serviceTask")
                .endEvent()
                .done();

        wrap(processModel).taskDefinition("serviceTask", "foo", 3);

        // create deployment
        workflowsClient
            .deploy()
            .bpmnModelInstance(processModel)
            .execute();

        try
        {
            Thread.sleep(1000);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Supplier<Future> requestFn(TngpClient client, TransportConnection connection)
    {
        final WorkflowTopicClient workflowsClient = client.workflowTopic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);

        return () ->
        {
            return workflowsClient.create()
                .bpmnProcessId("process")
                .executeAsync(connection);
        };
    }

    public static void main(String[] args)
    {
        new StartWorkflowInstanceLatencyTest().run();
    }
}
