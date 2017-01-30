package org.camunda.optimize.rest.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

/**
 *
 * @author Askar Akhmerov
 */
@Provider
@Component
public class OptimizeObjectMapperProvider implements ContextResolver<ObjectMapper> {
  @Autowired
  private ObjectMapper defaultObjectMapper;

  public ObjectMapper getContext(Class<?> type) {
    return defaultObjectMapper;
  }

}
