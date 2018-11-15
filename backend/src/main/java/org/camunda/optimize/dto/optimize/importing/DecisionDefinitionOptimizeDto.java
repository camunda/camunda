package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;

public class DecisionDefinitionOptimizeDto implements Serializable, OptimizeDto {
  private String id;
  private String key;
  private String version;
  private String name;
  private String dmn10Xml;
  private String engine;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDmn10Xml() {
    return dmn10Xml;
  }

  public void setDmn10Xml(String dmn10Xml) {
    this.dmn10Xml = dmn10Xml;
  }

  public String getEngine() {
    return engine;
  }

  public void setEngine(String engine) {
    this.engine = engine;
  }
}
