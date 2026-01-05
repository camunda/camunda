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
import io.camunda.gateway.protocol.model.AuthorizationRequest;
import io.camunda.gateway.protocol.model.BasicStringFilterProperty;
import io.camunda.gateway.protocol.model.BatchOperationItemStateFilterProperty;
import io.camunda.gateway.protocol.model.BatchOperationStateFilterProperty;
import io.camunda.gateway.protocol.model.BatchOperationTypeFilterProperty;
import io.camunda.gateway.protocol.model.CategoryFilterProperty;
import io.camunda.gateway.protocol.model.ClusterVariableScopeFilterProperty;
import io.camunda.gateway.protocol.model.DateTimeFilterProperty;
import io.camunda.gateway.protocol.model.DecisionEvaluationInstruction;
import io.camunda.gateway.protocol.model.DecisionInstanceStateFilterProperty;
import io.camunda.gateway.protocol.model.ElementInstanceStateFilterProperty;
import io.camunda.gateway.protocol.model.EntityTypeFilterProperty;
import io.camunda.gateway.protocol.model.IncidentErrorTypeFilterProperty;
import io.camunda.gateway.protocol.model.IncidentStateFilterProperty;
import io.camunda.gateway.protocol.model.IntegerFilterProperty;
import io.camunda.gateway.protocol.model.JobKindFilterProperty;
import io.camunda.gateway.protocol.model.JobListenerEventTypeFilterProperty;
import io.camunda.gateway.protocol.model.JobStateFilterProperty;
import io.camunda.gateway.protocol.model.MessageSubscriptionStateFilterProperty;
import io.camunda.gateway.protocol.model.OperationTypeFilterProperty;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationTerminateInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceStateFilterProperty;
import io.camunda.gateway.protocol.model.SearchQueryPageRequest;
import io.camunda.gateway.protocol.model.StringFilterProperty;
import io.camunda.gateway.protocol.model.UserTaskStateFilterProperty;
import io.camunda.zeebe.gateway.rest.deserializer.AuditLogCategoryFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.AuditLogEntityTypeFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.AuditLogOperationTypeFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.AuthorizationRequestDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.BasicStringFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.BatchOperatioItemStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.BatchOperationStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.BatchOperationTypeFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.ClusterVariableScopeFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.DateTimeFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.DecisionEvaluationInstructionDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.DecisionInstanceStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.ElementInstanceStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.IncidentErrorTypePropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.IncidentStatePropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.IntegerFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.JobKindFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.JobListenerEventTypeFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.JobStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.MessageSubscriptionStatePropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.ProcessInstanceCreationInstructionDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.ProcessInstanceModificationTerminateInstructionDeserializer;
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
        ClusterVariableScopeFilterProperty.class,
        new ClusterVariableScopeFilterPropertyDeserializer());
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
    module.addDeserializer(SearchQueryPageRequest.class, new SearchQueryPageRequestDeserializer());
    module.addDeserializer(
        ProcessInstanceCreationInstruction.class,
        new ProcessInstanceCreationInstructionDeserializer());
    module.addDeserializer(
        DecisionEvaluationInstruction.class, new DecisionEvaluationInstructionDeserializer());
    module.addDeserializer(
        DecisionInstanceStateFilterProperty.class,
        new DecisionInstanceStateFilterPropertyDeserializer());
    module.addDeserializer(
        IncidentErrorTypeFilterProperty.class, new IncidentErrorTypePropertyDeserializer());
    module.addDeserializer(
        IncidentStateFilterProperty.class, new IncidentStatePropertyDeserializer());
    module.addDeserializer(AuthorizationRequest.class, new AuthorizationRequestDeserializer());
    module.addDeserializer(
        OperationTypeFilterProperty.class, new AuditLogOperationTypeFilterPropertyDeserializer());
    module.addDeserializer(
        EntityTypeFilterProperty.class, new AuditLogEntityTypeFilterPropertyDeserializer());
    module.addDeserializer(
        CategoryFilterProperty.class, new AuditLogCategoryFilterPropertyDeserializer());
    module.addDeserializer(
        ProcessInstanceModificationTerminateInstruction.class,
        new ProcessInstanceModificationTerminateInstructionDeserializer());
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
              // this also prevents coercion for string target types from non-string types
              om.coercionConfigDefaults()
                  .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
            });
    gatewayRestObjectMapperCustomizer().accept(builder);
    return builder.build();
  }
}
