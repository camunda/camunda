package io.camunda.unifiedconfig;

import java.time.Duration;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

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

    Yaml yaml = new Yaml(new CustomRepresenter(options), options);
    String output = yaml.dump(this);
    System.out.println(output);
  }
}

// The following class describes how to print the objects of type:
// - Duration
class CustomRepresenter extends Representer {
  public CustomRepresenter(DumperOptions options) {
    super(options);
    this.representers.put(Duration.class, new RepresentDuration());
  }

  private static class RepresentDuration implements Represent {
    @Override
    public Node representData(Object data) {
      Duration d = (Duration) data;
      String formatted = formatDuration(d);
      return new ScalarNode(Tag.STR, formatted, null, null, DumperOptions.ScalarStyle.PLAIN);
    }

    private String formatDuration(Duration d) {
      if (!d.isZero()) {
        if (d.toHours() > 0) return d.toHours() + "h";
        if (d.toMinutes() > 0) return d.toMinutes() + "m";
        if (d.getSeconds() > 0) return d.getSeconds() + "s";
        if (d.toMillis() > 0) return d.toMillis() + "ms";
      }
      return "0s"; // Default fallback
    }
  }
}
