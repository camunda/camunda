package org.camunda.tngp.perftest;

import static org.camunda.tngp.test.util.bpmn.TngpModelInstance.wrap;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.WorkflowsClient;
import org.camunda.tngp.client.cmd.WorkflowDefinition;
import org.camunda.tngp.perftest.helper.MaxRateThroughputTest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;

public class StartWorkflowInstanceThroughputTest extends MaxRateThroughputTest
{
    protected long wfDefinitionId;

    @Override
    protected void executeSetup(Properties properties, TngpClient client)
    {
        final WorkflowsClient workflowsClient = client.workflows();

        final BpmnModelInstance processModel = Bpmn.createExecutableProcess()
                .startEvent()
                .serviceTask("serviceTask")
                .endEvent()
                .done();

        wrap(processModel).taskAttributes("serviceTask", "foo", 0);

        // create deployment
        final WorkflowDefinition deployedWorkflow = workflowsClient
                .deploy()
                .bpmnModelInstance(processModel)
                .execute();

        this.wfDefinitionId = deployedWorkflow.getId();

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
        final WorkflowsClient workflows = client.workflows();

        return () ->
        {
            return workflows.start()
                .workflowDefinitionId(wfDefinitionId)
                .executeAsync(connection);
        };
    }

    public static void main(String[] args)
    {
        new StartWorkflowInstanceThroughputTest().run();
    }
}
