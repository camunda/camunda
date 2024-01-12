package org.sample;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.json.OperateEntityFactory;
import io.camunda.zeebe.exporter.operate.NoSpringJacksonConfig;

@State(Scope.Thread)
public class JsonSerializationState {

  private OperateEntityFactory entityFactory;
  private ObjectMapper objectMapper;
  
  public JsonSerializationState() {
    this.entityFactory  = new OperateEntityFactory();
    this.objectMapper = NoSpringJacksonConfig.buildObjectMapper();
  }
  
  public OperateEntityFactory getEntityFactory() {
    return entityFactory;
  }
  
  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
