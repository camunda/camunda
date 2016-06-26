package org.camunda.tngp.client;

import static org.camunda.tngp.client.ClientProperties.*;

import java.util.Properties;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.client.cmd.DeployedWorkflowType;

public class DeployBpmnResourceClient
{

    public static void main(String[] args)
    {
        final Properties properties = new Properties();

        properties.put(BROKER_CONTACTPOINT, "127.0.0.1:8880");

        try (TngpClient client = TngpClient.create(properties))
        {
            client.connect();

            final ProcessService workflowService = client.processes();

            final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("testProcess")
                .startEvent()
                .serviceTask()
                .endEvent()
                .done();

            final DeployedWorkflowType deployedWorkflowType = workflowService.deploy()
                .bpmnModelInstance(bpmnModelInstance)
                .execute();

            System.out.println("Deployed workflow type " + deployedWorkflowType.getWorkflowTypeId());

        }

    }

}
