package org.camunda.optimize.upgrade;

import org.apache.http.HttpHost;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
  private boolean createSnapshot;
  private RestClient restClient;
  private Map<String, UpgradeStepExecutor> executorMap = new HashMap<>();

  public UpgradeService(AbstractUpgradePlan upgradePlan, String[] args) {
    this.upgradePlan = upgradePlan;
    this.arguments = args;

    //initialize context based on arguments
    if (args != null) {
      for (String argument : args) {
        if (SchemaUpgradeUtil.CREATE_SNAPSHOT.equals(argument)) {
          this.createSnapshot = true;
        }
      }
    }

    //TODO: customize location based on args
    configurationService = new ConfigurationService();
    this.restClient = initClient();
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
