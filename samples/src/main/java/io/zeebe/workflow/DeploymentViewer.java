package io.zeebe.workflow;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.clients.WorkflowClient;
import io.zeebe.client.api.commands.WorkflowResource;
import io.zeebe.client.api.commands.Workflows;

public class DeploymentViewer
{

    public static void main(final String[] args)
    {
        final ZeebeClient client = ZeebeClient.newClientBuilder().build();

        final WorkflowClient workflowClient = client.topicClient().workflowClient();

        final Workflows workflows = workflowClient.newWorkflowRequest().send().join();

        workflows.getWorkflows().forEach(wf -> {
            System.out.println("Fetching workflow resource for " + wf);

            final WorkflowResource resource = workflowClient.newResourceRequest()
                .workflowKey(wf.getWorkflowKey())
                .send().join();

            System.out.println(resource);
        });

        client.close();
    }

}
