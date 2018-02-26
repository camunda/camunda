package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.KeyProcessDefinitionOptimizeDto;

import java.io.Serializable;

public class ProcessDefinitionOptimizeDto
    extends KeyProcessDefinitionOptimizeDto
    implements Serializable, OptimizeDto {

  protected String id;
  protected String name;
  protected long version;
  protected String engine;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }

  public String getEngine() {
    return engine;
  }

  public void setEngine(String engine) {
    this.engine = engine;
  }
}
