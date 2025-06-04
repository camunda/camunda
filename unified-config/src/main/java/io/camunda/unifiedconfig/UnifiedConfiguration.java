package io.camunda.unifiedconfig;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public class UnifiedConfiguration {
  private Camunda camunda;

  public Camunda getCamunda() {
    return camunda;
  }

  public void setCamunda(Camunda camunda) {
    this.camunda = camunda;
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
