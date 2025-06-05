package io.camunda.unifiedconfig;

import org.springframework.core.env.Environment;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public class UnifiedConfiguration {
  private Camunda camunda = new Camunda();
  private Cluster cluster = new Cluster();

  public Camunda getCamunda() {
    return camunda;
  }

  public Cluster getCluster() {
    return cluster;
  }

  public void setCamunda(Camunda camunda) {
    this.camunda = camunda;
  }

  public void setCluster(Cluster cluster) {
    this.cluster = cluster;
  }

  public void printFullConfigurationAsYaml() {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);

    Yaml yaml = new Yaml(options);
    String output = yaml.dump(this);
    System.out.println(output);
  }
}
