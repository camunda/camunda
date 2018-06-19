package org.camunda.operate.zeebe;

import org.camunda.operate.property.OperateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.clients.WorkflowClient;
import io.zeebe.client.api.commands.WorkflowResource;
import io.zeebe.client.api.commands.Workflows;

/**
 * Temporary solution to load workflows and it will soon be changed in ZeebeClient to subscription style.
 * Currently we're requesting for the workflows each 3 seconds.
 * @author Svetlana Dorokhova.
 */
@Component
@Configuration
@EnableScheduling
@Profile("zeebe")
public class ZeebeWorkflowLoader {

  @Autowired
  private WorflowEventTransformer workflowEventTransformer;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ZeebeClient zeebeClient;

  @Scheduled(cron="*/5 * * * * *")
  public void loadWorkflows() {
    for (String topic : operateProperties.getZeebe().getTopics()) {
      final WorkflowClient workflowClient =
        zeebeClient.topicClient(topic).workflowClient();
      final Workflows workflows =
        workflowClient
          .newWorkflowRequest()
          .send().join();
      workflows.getWorkflows().forEach(wf -> {
        final WorkflowResource resource =
          workflowClient
            .newResourceRequest()
            .workflowKey(wf.getWorkflowKey())
            .send().join();
        try {
          workflowEventTransformer.onWorkflowEvent(topic, wf, resource);
        } catch (InterruptedException e) {
          //TODO
        }
      });
    }
  }

}
