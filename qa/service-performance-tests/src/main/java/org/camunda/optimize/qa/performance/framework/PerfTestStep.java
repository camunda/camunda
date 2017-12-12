package org.camunda.optimize.qa.performance.framework;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.camunda.optimize.service.util.CustomDeserializer;
import org.camunda.optimize.service.util.CustomSerializer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public abstract class PerfTestStep {
  protected ObjectMapper objectMapper;

  protected ObjectMapper initMapper(PerfTestContext context) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(context.getConfiguration().getDateFormat());
    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class, new CustomSerializer(formatter));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new CustomDeserializer(formatter));

    ObjectMapper objectMapper;
    objectMapper = Jackson2ObjectMapperBuilder
        .json()
        .modules(javaTimeModule)
        .featuresToDisable(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
            DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
        )
        .featuresToEnable(
            JsonParser.Feature.ALLOW_COMMENTS,
            SerializationFeature.INDENT_OUTPUT
        )
        .build();
    return objectMapper;
  }


  public Class getTestStepClass() {
    return this.getClass();
  }

  public abstract PerfTestStepResult execute(PerfTestContext  context);

}
