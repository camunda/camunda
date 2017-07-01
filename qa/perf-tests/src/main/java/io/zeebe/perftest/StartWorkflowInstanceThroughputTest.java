package io.zeebe.perftest;


import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.WorkflowTopicClient;
import io.zeebe.perftest.helper.MaxRateThroughputTest;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.wrap;
import static io.zeebe.perftest.CommonProperties.DEFAULT_PARTITION_ID;
import static io.zeebe.perftest.CommonProperties.DEFAULT_TOPIC_NAME;


public class StartWorkflowInstanceThroughputTest extends MaxRateThroughputTest
{


    @Override
    protected void executeSetup(Properties properties, ZeebeClient client)
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
            throw new RuntimeException(e);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Supplier<Future> requestFn(ZeebeClient client)
    {
        final WorkflowTopicClient workflows = client.workflowTopic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);

        return () ->
        {
            return workflows.create()
                .bpmnProcessId("process")
                .executeAsync();
        };
    }

    public static void main(String[] args)
    {
        new StartWorkflowInstanceThroughputTest().run();
    }
}
