package org.camunda.optimize.upgrade.plan;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import org.apache.http.HttpHost;
import org.camunda.optimize.service.es.schema.type.MetadataType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.camunda.optimize.upgrade.main.UpgradeMain;
import org.camunda.optimize.upgrade.service.ValidationService;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
    defineLogbackLoggingConfiguration();
    configurationService = new ConfigurationService();
    validationService = new ValidationService(configurationService);
    validationService.validateExecutionPath();
    client = initClient();

  }

  private void addEnvironmentFolderToClasspath() throws Exception {
    String location = ".." + "/" + "environment";
    String pathToJarExecutable = UpgradeMain.class
      .getProtectionDomain()
      .getCodeSource()
      .getLocation()
      .toURI()
      .getPath();
    pathToJarExecutable = pathToJarExecutable.replaceAll("%20", " ");
    String executionPath = removeJarFileNameFromPath(pathToJarExecutable);
    addDirectoryToClasspath(executionPath + location);
  }

  /**
   * Takes a path like '/home/user/Optimize/upgrade/upgrade-optimize-2.2.0-SNAPSHOT.jar' and
   * removes the filename from it, which would result in '/home/user/Optimize/upgrade/'
   */
  private String removeJarFileNameFromPath(String executionFolderPath) {
    if (executionFolderPath.trim().endsWith("jar")) {
      int i = executionFolderPath.lastIndexOf("/");
      executionFolderPath = executionFolderPath.substring(0, i) + "/";
    }
    return executionFolderPath;
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

  private void defineLogbackLoggingConfiguration() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.reset();
    JoranConfigurator configurator = new JoranConfigurator();
    InputStream configStream = null;
    try {
      configStream = getLogbackConfigurationFileStream();
      if (configStream != null) {
        configurator.setContext(loggerContext);
        configurator.doConfigure(configStream); // loads logback file
        configStream.close();
      }
    } catch (JoranException | IOException e) {
      // if no logging is configured don't do anything
    } finally {
      if (configStream != null) {
        try {
          configStream.close();
        } catch (IOException e) {
          logger.error("error closing stream", e);
        }
      }
    }
  }

  private InputStream getLogbackConfigurationFileStream() {
    InputStream stream  = this.getClass().getClassLoader().getResourceAsStream("environment-logback.xml");
    if(stream != null) {
      return stream;
    }
    stream = this.getClass().getClassLoader().getResourceAsStream("logback-test.xml");
    if(stream != null) {
      return stream;
    }
    stream = this.getClass().getClassLoader().getResourceAsStream("logback.xml");
    if(stream != null) {
      return stream;
    }
    return null;
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
    logger.info("Updating Elasticsearch data structure version tag from {} to {}.", fromVersion, toVersion);
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
