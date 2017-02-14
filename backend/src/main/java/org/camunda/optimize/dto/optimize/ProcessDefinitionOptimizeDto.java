package org.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProcessDefinitionOptimizeDto implements Serializable, OptimizeDto {

  protected String id;
  protected String key;
  protected String name;

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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

}
