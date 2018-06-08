package org.camunda.operate.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

/**
 * This class contains all project configuration parameters.
 * @author Svetlana Dorokhova.
 */
@Component
@ConfigurationProperties(OperateProperties.PREFIX)
public class OperateProperties {

  public static final String PREFIX = "camunda.bpm.operate";

  /**
   *  Configuration parameters for elasticsearch client.
   */
  @NestedConfigurationProperty
  private ElasticsearchProperties elasticsearch = new ElasticsearchProperties();

  public ElasticsearchProperties getElasticsearch() {
    return elasticsearch;
  }

  public void setElasticsearch(ElasticsearchProperties elasticsearch) {
    this.elasticsearch = elasticsearch;
  }
}
