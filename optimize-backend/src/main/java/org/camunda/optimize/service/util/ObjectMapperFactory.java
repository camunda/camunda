package org.camunda.optimize.service.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

/**
 * @author Askar Akhmerov
 */
@Component
public class ObjectMapperFactory {
  private ObjectMapper result;
  /**
   * please not that instantiation is delegated to spring
   * @return
   */
  public ObjectMapper createDefaultMapper() {
    if (result == null) {
      result = new ObjectMapper();
      result.configure(SerializationFeature.INDENT_OUTPUT, true);
    }
    return result;
  }
}
