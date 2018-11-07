package org.camunda.operate.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Workflows;


public abstract class AbstractDataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(AbstractDataGenerator.class);


  @Autowired
  protected ZeebeClient client;


  public void createZeebeData(boolean manuallyCalled) {
  }

  public boolean shouldCreateData(boolean manuallyCalled) {
    if (!manuallyCalled) {    //when called manually, always create the data
      final Workflows workflows = client.workflowClient().newWorkflowRequest().send().join();
      if (workflows != null && workflows.getWorkflows().size() > 0) {
        //data already exists
        logger.debug("Data already exists in Zeebe.");
        return false;
      }
    }
    return true;
  }


}
