package org.camunda.optimize.rest.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.service.util.ObjectMapperFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

/**
 *
 * @author Askar Akhmerov
 */
@Provider
@Produces(MediaType.APPLICATION_JSON)
@Component
public class OptimizeObjectMapperProvider implements ContextResolver<ObjectMapper> {
  @Autowired
  private ObjectMapperFactory objectMapperFactory;

  public ObjectMapper getContext(Class<?> type) {
    return objectMapperFactory.createEngineMapper();
  }

}
