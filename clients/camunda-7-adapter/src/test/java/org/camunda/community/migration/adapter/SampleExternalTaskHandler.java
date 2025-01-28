package org.camunda.community.migration.adapter;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@ExternalTaskSubscription(topicName = "test-topic")
@Component
public class SampleExternalTaskHandler implements ExternalTaskHandler {
  private static final Logger LOG = LoggerFactory.getLogger(SampleExternalTaskHandler.class);
  public static String someVariable;
  public static boolean executed = false;

  @Override
  public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
    LOG.info("Called from process instance {}", externalTask.getProcessInstanceId());
    someVariable = externalTask.getVariable("someVariable");
    executed = true;
    externalTaskService.complete(externalTask);
  }
}
