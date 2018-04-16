package org.camunda.optimize.upgrade.service;

import org.apache.http.HttpHost;
import org.camunda.optimize.upgrade.plan.AbstractUpgradePlan;
import org.camunda.optimize.upgrade.UpgradeStep;
import org.camunda.optimize.upgrade.UpgradeStepExecutor;
import org.camunda.optimize.upgrade.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.executor.AddFieldExecutor;
import org.camunda.optimize.upgrade.executor.CreateIndexExecutor;
import org.camunda.optimize.upgrade.executor.ChangeFieldTypeExecutor;
import org.camunda.optimize.upgrade.executor.DeleteFieldExecutor;
import org.camunda.optimize.upgrade.executor.DeleteIndexExecutor;
import org.camunda.optimize.upgrade.executor.InsertDataExecutor;
import org.camunda.optimize.upgrade.executor.RenameFieldExecutor;
import org.camunda.optimize.upgrade.executor.RenameIndexExecutor;
import org.camunda.optimize.upgrade.executor.UpdateDataExecutor;
import org.camunda.optimize.upgrade.steps.AddFieldStep;
import org.camunda.optimize.upgrade.steps.ChangeFieldTypeStep;
import org.camunda.optimize.upgrade.steps.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.DeleteFieldStep;
import org.camunda.optimize.upgrade.steps.DeleteIndexStep;
import org.camunda.optimize.upgrade.steps.InsertDataStep;
import org.camunda.optimize.upgrade.steps.RenameFieldStep;
import org.camunda.optimize.upgrade.steps.RenameIndexStep;
import org.camunda.optimize.upgrade.steps.UpdateDataStep;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.client.RestClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Askar Akhmerov
 */
public class UpgradeService {

  public static final String HTTP = "http";
  private final AbstractUpgradePlan upgradePlan;
  private final String[] arguments;

  //fields actually belong to context
  private ConfigurationService configurationService;
  private ValidationService validationService;
  private boolean createSnapshot;
  private RestClient restClient;
  private Map<String, UpgradeStepExecutor> executorMap = new HashMap<>();

  public UpgradeService(AbstractUpgradePlan upgradePlan, String[] args, ValidationService validationService) {
    this.upgradePlan = upgradePlan;
    this.arguments = args;
    this.validationService = validationService;

    String configLocation = null;
    if (args != null) {
      List<String> argsList = Arrays.asList(args);
      int configPosition = argsList.indexOf("--config");
      if (configPosition >= 0) {
         configLocation = argsList.get(configPosition + 1);
      }

      if (argsList.contains("--snapshot")) {
        this.createSnapshot = true;
      }
    }

    configurationService = configLocation == null ?
      new ConfigurationService() : new ConfigurationService(new String[]{configLocation});
    this.restClient = initClient();

    if (validationService != null) {
      validationService.validateVersions(restClient);
    }
  }

  public UpgradeService(AbstractUpgradePlan upgradePlan, String[] args) {
    this(upgradePlan, args, null);
  }

  private void initExecutors(RestClient restClient) {
    if (executorMap.size() == 0) {
      executorMap.put(ChangeFieldTypeStep.NAME, new ChangeFieldTypeExecutor(restClient, configurationService.getDateFormat()));
      executorMap.put(CreateIndexStep.NAME, new CreateIndexExecutor(restClient, configurationService.getDateFormat()));
      executorMap.put(RenameFieldStep.NAME, new RenameFieldExecutor(restClient, configurationService.getDateFormat()));
      executorMap.put(RenameIndexStep.NAME, new RenameIndexExecutor(restClient, configurationService.getDateFormat()));
      executorMap.put(AddFieldStep.NAME, new AddFieldExecutor(restClient, configurationService.getDateFormat()));
      executorMap.put(DeleteFieldStep.NAME, new DeleteFieldExecutor(restClient, configurationService.getDateFormat()));

      executorMap.put(InsertDataStep.NAME, new InsertDataExecutor(restClient));
      executorMap.put(DeleteIndexStep.NAME, new DeleteIndexExecutor(restClient));
      executorMap.put(UpdateDataStep.NAME, new UpdateDataExecutor(restClient));
    }
  }

  private RestClient initClient() {
    return RestClient.builder(
      new HttpHost(
        configurationService.getClientHost(),
        configurationService.getClientPort(),
        HTTP
      )
    ).build();
  }

  public void execute() {
    this.execute(null);
  }

  public void execute(Consumer<UpgradeStep> postProcess) {
    this.initExecutors(restClient);

    for (UpgradeStep step : upgradePlan.getUpgradeSteps()) {
      this.executorMap.computeIfPresent(step.getName(), (stepName, executor) -> {
        try {
          executor.execute(step);

          if (postProcess != null) {
            postProcess.accept(step);
          }

        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        return executor;
      });
    }
  }

  public void setConfigurationService(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }
}
