package org.camunda.optimize.upgrade.plan;

import org.apache.http.HttpHost;
import org.camunda.optimize.service.es.schema.type.MetadataType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.camunda.optimize.upgrade.main.UpgradeFrom21To22;
import org.camunda.optimize.upgrade.service.ValidationService;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;


public class UpgradeExecutionPlan implements UpgradePlan {

  public static final String HTTP = "http";

  private Logger logger = LoggerFactory.getLogger(getClass());

  private RestClient client;
  private ConfigurationService configurationService;
  private List<UpgradeStep> upgradeSteps = new ArrayList<>();
  private String fromVersion;
  private String toVersion;
  private ValidationService validationService;

  public UpgradeExecutionPlan() throws Exception {
    addEnvironmentFolderToClasspath();
    configurationService = new ConfigurationService();
    validationService = new ValidationService(configurationService);
    validationService.validateExecutionPath();
    client = initClient();

  }

  private void addEnvironmentFolderToClasspath() throws Exception {
    String location = ".." + File.separator + "environment";
    String executionFolderPath = UpgradeFrom21To22.class
      .getProtectionDomain()
      .getCodeSource()
      .getLocation()
      .toURI()
      .getPath();
    executionFolderPath = executionFolderPath.replaceAll("%20", " ");
    addDirectoryToClasspath(executionFolderPath + location);
  }

  private void addDirectoryToClasspath(String s) throws Exception {
    //need to do add path to Classpath with reflection since the URLClassLoader.addURL(URL url) method is protected
    // taken from: https://stackoverflow.com/a/21931044
    File f = new File(s);
    URI u = f.toURI();
    URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
    Class<URLClassLoader> urlClass = URLClassLoader.class;
    Method method = urlClass.getDeclaredMethod("addURL", URL.class);
    method.setAccessible(true);
    method.invoke(urlClassLoader, u.toURL());
  }

  private RestClient initClient() {
    return RestClient.builder(
      new HttpHost(
        configurationService.getElasticSearchHost(),
        configurationService.getElasticSearchHttpPort(),
        HTTP
      )
    ).build();
  }

  @Override
  public void execute() {
    validationService.validateVersions(client, fromVersion, toVersion);
    ESIndexAdjuster ESIndexAdjuster = new ESIndexAdjuster(client, configurationService);
    for (UpgradeStep step : upgradeSteps) {
      logger.info("Performing {}.", step.getClass().getSimpleName());
      step.execute(ESIndexAdjuster);
      logger.info("{} has successfully being executed.", step.getClass().getSimpleName());
    }
    updateOptimizeVersion(ESIndexAdjuster);
  }

  public void updateOptimizeVersion(ESIndexAdjuster ESIndexAdjuster) {
    ESIndexAdjuster.updateData(
      configurationService.getMetaDataType(),
      termQuery(MetadataType.SCHEMA_VERSION, fromVersion),
      String.format("ctx._source.schemaVersion = \"%s\"", toVersion)
    );
  }

  public void addUpgradeStep(UpgradeStep upgradeStep) {
    this.upgradeSteps.add(upgradeStep);
  }

  public void setFromVersion(String fromVersion) {
    this.fromVersion = fromVersion;
  }

  public void setToVersion(String toVersion) {
    this.toVersion = toVersion;
  }

}
