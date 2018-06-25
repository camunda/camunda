package org.camunda.operate.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

/**
 * This class contains all project configuration parameters.
 */
@Component
@ConfigurationProperties(OperateProperties.PREFIX)
public class OperateProperties {

  public static final String PREFIX = "camunda.bpm.operate";

  private boolean startLoadingDataOnStartup = true;

  /**
   *  Configuration parameters for elasticsearch client.
   */
  @NestedConfigurationProperty
  private ElasticsearchProperties elasticsearch = new ElasticsearchProperties();

  @NestedConfigurationProperty
  private ZeebeProperties zeebe = new ZeebeProperties();

  public boolean isStartLoadingDataOnStartup() {
    return startLoadingDataOnStartup;
  }

  public void setStartLoadingDataOnStartup(boolean startLoadingDataOnStartup) {
    this.startLoadingDataOnStartup = startLoadingDataOnStartup;
  }

  public ElasticsearchProperties getElasticsearch() {
    return elasticsearch;
  }

  public void setElasticsearch(ElasticsearchProperties elasticsearch) {
    this.elasticsearch = elasticsearch;
  }

  public ZeebeProperties getZeebe() {
    return zeebe;
  }

  public void setZeebe(ZeebeProperties zeebe) {
    this.zeebe = zeebe;
  }
}
