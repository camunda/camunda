/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.data;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static io.camunda.operate.util.ThreadUtil.sleepFor;

import io.camunda.operate.data.usertest.UserTestDataGenerator;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.ZeebeStore;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractDataGenerator implements DataGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDataGenerator.class);
  @Autowired protected ZeebeClient client;
  @Autowired protected OperateProperties operateProperties;
  protected boolean manuallyCalled = false;
  protected ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
  private boolean shutdown = false;
  @Autowired private ZeebeStore zeebeStore;

  @PostConstruct
  private void startDataGenerator() {
    startGeneratingData();
  }

  protected void startGeneratingData() {
    LOGGER.debug("INIT: Generate demo data...");
    try {
      createZeebeDataAsync(false);
    } catch (Exception ex) {
      LOGGER.debug("Demo data could not be generated. Cause: {}", ex.getMessage());
      LOGGER.error("Error occurred when generating demo data.", ex);
    }
  }

  @PreDestroy
  public void shutdown() {
    LOGGER.info("Shutdown DataGenerator");
    shutdown = true;
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(200, TimeUnit.MILLISECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (final InterruptedException e) {
        scheduler.shutdownNow();
      }
    }
  }

  @Override
  public void createZeebeDataAsync(final boolean manuallyCalled) {
    scheduler.execute(
        () -> {
          Boolean zeebeDataCreated = null;
          while (zeebeDataCreated == null && !shutdown) {
            try {
              zeebeDataCreated = createZeebeData(manuallyCalled);
            } catch (final Exception ex) {
              LOGGER.error(
                  String.format(
                      "Error occurred when creating demo data: %s. Retrying...", ex.getMessage()),
                  ex);
              sleepFor(2000);
            }
          }
        });
  }

  public boolean createZeebeData(final boolean manuallyCalled) {
    this.manuallyCalled = manuallyCalled;

    if (!shouldCreateData(manuallyCalled)) {
      return false;
    }

    return true;
  }

  public boolean shouldCreateData(final boolean manuallyCalled) {
    if (!manuallyCalled) { // when called manually, always create the data
      final boolean exists =
          zeebeStore.zeebeIndicesExists(
              operateProperties.getZeebeElasticsearch().getPrefix() + "*");
      if (exists) {
        // data already exists
        LOGGER.debug("Data already exists in Zeebe.");
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("checkstyle:MissingSwitchDefault")
  protected JobWorker progressSimpleTask(final String taskType) {
    return client
        .newWorker()
        .jobType(taskType)
        .handler(
            (jobClient, job) -> {
              final int scenarioCount = ThreadLocalRandom.current().nextInt(3);
              switch (scenarioCount) {
                case 0:
                  // timeout
                  break;
                case 1:
                  // successfully complete task
                  jobClient.newCompleteCommand(job.getKey()).send().join();
                  break;
                case 2:
                  // fail task -> create incident
                  jobClient.newFailCommand(job.getKey()).retries(0).send().join();
                  break;
              }
            })
        .name("operate")
        .timeout(Duration.ofSeconds(UserTestDataGenerator.JOB_WORKER_TIMEOUT))
        .open();
  }

  protected JobWorker progressSimpleTask(final String taskType, final int retriesLeft) {
    return client
        .newWorker()
        .jobType(taskType)
        .handler(
            (jobClient, job) ->
                jobClient.newFailCommand(job.getKey()).retries(retriesLeft).send().join())
        .name("operate")
        .timeout(Duration.ofSeconds(UserTestDataGenerator.JOB_WORKER_TIMEOUT))
        .open();
  }

  protected String getTenant(final String tenantId) {
    if (operateProperties.getMultiTenancy().isEnabled()) {
      return tenantId;
    }
    return DEFAULT_TENANT_ID;
  }
}
