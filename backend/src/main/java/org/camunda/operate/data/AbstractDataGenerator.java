package org.camunda.operate.data;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Workflows;


public abstract class AbstractDataGenerator implements DataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(AbstractDataGenerator.class);

  @Autowired
  protected ZeebeClient client;

  protected boolean manuallyCalled = false;

  protected ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);;

  @Override
  public void createZeebeDataAsync(boolean manuallyCalled) {
    scheduler.execute(() -> {
      Boolean zeebeDataCreated = null;
      while (zeebeDataCreated == null) {
        try {
          zeebeDataCreated = createZeebeData(manuallyCalled);
        } catch (Exception ex) {
          logger.error(String.format("Error occurred when creating demo data: %s. Retrying...", ex.getMessage()), ex);
          try {
            Thread.sleep(2000);
          } catch (InterruptedException ex2) {
            //
          }
        }
      }
    });
  }

  public boolean createZeebeData(boolean manuallyCalled) {
    this.manuallyCalled = manuallyCalled;

    if (!shouldCreateData(manuallyCalled)) {
      return false;
    }

    return true;
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
