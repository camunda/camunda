package org.camunda.tngp.perftest;


import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.WorkflowTopicClient;
import org.camunda.tngp.perftest.helper.MaxRateThroughputTest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;

public class StartWorkflowInstanceThroughputTest extends MaxRateThroughputTest
{

    @Override
    protected void executeSetup(Properties properties, TngpClient client)
    {
        final WorkflowTopicClient workflowsClient = client.workflowTopic(0);

        final BpmnModelInstance processModel = Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("serviceTask")
                .endEvent()
                .done();

        wrap(processModel).taskDefinition("serviceTask", "foo", 0);

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
            throw new RuntimeException(e);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Supplier<Future> requestFn(TngpClient client, TransportConnection connection)
    {
        final WorkflowTopicClient workflows = client.workflowTopic(0);

        return () ->
        {
            return workflows.create()
                .bpmnProcessId("process")
                .executeAsync(connection);
        };
    }

    public static void main(String[] args)
    {
        new StartWorkflowInstanceThroughputTest().run();
    }
}
