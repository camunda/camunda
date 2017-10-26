package org.camunda.optimize.service.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author Askar Akhmerov
 */
@Component
public class ObjectMapperFactory {
  private ObjectMapper result;
  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  /**
   * please not that instantiation is delegated to spring
   * @return
   */
  public ObjectMapper createDefaultMapper() {
    if (result == null) {
      result = new ObjectMapper();
      result.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
      result.configure(SerializationFeature.INDENT_OUTPUT, true);
      result.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
      result.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      result.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
      DateFormat df = new SimpleDateFormat(configurationService.getDateFormat());
      result.setDateFormat(df);
      JavaTimeModule javaTimeModule = new JavaTimeModule();
      javaTimeModule.addSerializer(LocalDateTime.class,
        new LocalDateTimeSerializer(dateTimeFormatter));
      javaTimeModule.addDeserializer(LocalDateTime.class,
        new LocalDateTimeDeserializer(dateTimeFormatter));
      result.registerModule(javaTimeModule);
    }
    return result;
  }
}
