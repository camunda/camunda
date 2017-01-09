package org.camunda.optimize.rest.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

/**
 * @author Askar Akhmerov
 */
@Provider
public class OptimizeObjectMapperProvider implements ContextResolver<ObjectMapper> {
  final ObjectMapper defaultObjectMapper;

  public OptimizeObjectMapperProvider() {
    defaultObjectMapper = createDefaultMapper();
  }

  public ObjectMapper getContext(Class<?> type) {
    return defaultObjectMapper;
  }

  private static ObjectMapper createDefaultMapper() {
    final ObjectMapper result = new ObjectMapper();
    result.configure(SerializationFeature.INDENT_OUTPUT, true);

    return result;
  }
}
