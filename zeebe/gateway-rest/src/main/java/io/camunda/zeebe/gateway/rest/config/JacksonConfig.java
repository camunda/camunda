/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.gateway.protocol.rest.BasicStringFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationItemStateFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationStateFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationTypeFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.DateTimeFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ElementInstanceStateFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.IntegerFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.JobKindFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.JobListenerEventTypeFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.JobStateFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.MessageSubscriptionStateFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCreationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceStateFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageRequest;
import io.camunda.zeebe.gateway.protocol.rest.StringFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskStateFilterProperty;
import io.camunda.zeebe.gateway.rest.deserializer.BasicStringFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.BatchOperatioItemStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.BatchOperationStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.BatchOperationTypeFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.DateTimeFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.DecisionEvaluationInstructionDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.ElementInstanceStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.IntegerFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.JobKindFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.JobListenerEventTypeFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.JobStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.MessageSubscriptionStatePropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.ProcessInstanceCreationInstructionDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.ProcessInstanceStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.SearchQueryPageRequestDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.StringFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.UserTaskStateFilterPropertyDeserializer;
import java.util.function.Consumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {

  @Bean("gatewayRestObjectMapperCustomizer")
  public Consumer<Jackson2ObjectMapperBuilder> gatewayRestObjectMapperCustomizer() {
    final var module = new SimpleModule("gateway-rest-module");
    module.addDeserializer(
        BasicStringFilterProperty.class, new BasicStringFilterPropertyDeserializer());
    module.addDeserializer(IntegerFilterProperty.class, new IntegerFilterPropertyDeserializer());
    module.addDeserializer(StringFilterProperty.class, new StringFilterPropertyDeserializer());
    module.addDeserializer(DateTimeFilterProperty.class, new DateTimeFilterPropertyDeserializer());
    module.addDeserializer(
        ProcessInstanceStateFilterProperty.class,
        new ProcessInstanceStateFilterPropertyDeserializer());
    module.addDeserializer(
        ElementInstanceStateFilterProperty.class,
        new ElementInstanceStateFilterPropertyDeserializer());
    module.addDeserializer(
        BatchOperationStateFilterProperty.class,
        new BatchOperationStateFilterPropertyDeserializer());
    module.addDeserializer(
        BatchOperationTypeFilterProperty.class, new BatchOperationTypeFilterPropertyDeserializer());
    module.addDeserializer(
        BatchOperationItemStateFilterProperty.class,
        new BatchOperatioItemStateFilterPropertyDeserializer());
    module.addDeserializer(JobStateFilterProperty.class, new JobStateFilterPropertyDeserializer());
    module.addDeserializer(JobKindFilterProperty.class, new JobKindFilterPropertyDeserializer());
    module.addDeserializer(
        JobListenerEventTypeFilterProperty.class,
        new JobListenerEventTypeFilterPropertyDeserializer());
    module.addDeserializer(
        MessageSubscriptionStateFilterProperty.class,
        new MessageSubscriptionStatePropertyDeserializer());
    module.addDeserializer(
        UserTaskStateFilterProperty.class, new UserTaskStateFilterPropertyDeserializer());
    module.addDeserializer(
        ProcessInstanceCreationInstruction.class,
        new ProcessInstanceCreationInstructionDeserializer());
    module.addDeserializer(
        DecisionEvaluationInstruction.class, new DecisionEvaluationInstructionDeserializer());
    module.addDeserializer(SearchQueryPageRequest.class, new SearchQueryPageRequestDeserializer());
    return builder -> builder.modulesToInstall(modules -> modules.add(module));
  }

  @Bean("gatewayRestObjectMapper")
  public ObjectMapper objectMapper() {
    final var builder = Jackson2ObjectMapperBuilder.json();
    builder
        .featuresToDisable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .modulesToInstall(new JavaTimeModule(), new Jdk8Module())
        .postConfigurer(
            om -> {
              // this also prevents coercion for string target types
              om.coercionConfigDefaults()
                  .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
            });
    gatewayRestObjectMapperCustomizer().accept(builder);
    return builder.build();
  }
}
