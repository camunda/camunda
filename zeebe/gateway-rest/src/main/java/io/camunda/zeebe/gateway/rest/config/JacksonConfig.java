/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.zeebe.gateway.protocol.rest.BasicStringFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationItemStateFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationStateFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationTypeFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.DateTimeFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.ElementInstanceStateFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.IntegerFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.JobKindFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.JobListenerEventTypeFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.JobStateFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.MessageSubscriptionTypeFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceStateFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.StringFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskStateFilterProperty;
import io.camunda.zeebe.gateway.rest.deserializer.BasicStringFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.BatchOperatioItemStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.BatchOperationStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.BatchOperationTypeFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.DateTimeFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.ElementInstanceStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.IntegerFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.JobKindFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.JobListenerEventTypeFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.JobStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.MessageSubscriptionTypePropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.ProcessInstanceStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.StringFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.UserTaskStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.validation.OneOfGroup;
import io.camunda.zeebe.gateway.validation.runtime.jackson.TokenCaptureModule;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
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
        MessageSubscriptionTypeFilterProperty.class,
        new MessageSubscriptionTypePropertyDeserializer());
    module.addDeserializer(
        UserTaskStateFilterProperty.class, new UserTaskStateFilterPropertyDeserializer());
                        return builder -> builder.modulesToInstall(modules -> {
                                modules.add(module);
                                try {
                                    final ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
                                    scanner.addIncludeFilter(new AnnotationTypeFilter(OneOfGroup.class));
                                    final java.util.List<Class<?>> targets = new java.util.ArrayList<>();
                                    // Scan protocol REST package (adjust base packages if needed)
                                    for (var bd : scanner.findCandidateComponents("io.camunda.zeebe.gateway.protocol.rest")) {
                                        try {
                                            final Class<?> c = Class.forName(bd.getBeanClassName());
                                            final OneOfGroup ann = c.getAnnotation(OneOfGroup.class);
                                            if (ann != null && (ann.captureRawTokens() || ann.strictTokenKinds())) {
                                                targets.add(c);
                                            }
                                        } catch (ClassNotFoundException ignored) { }
                                    }
                                    modules.add(TokenCaptureModule.forClasses(targets.toArray(Class[]::new))); // owner: validation
                                } catch (Exception e) {
                                    modules.add(TokenCaptureModule.forClasses()); // fallback
                                }
                        });
  }

  @Bean("gatewayRestObjectMapper")
  public ObjectMapper objectMapper() {
    final var builder = Jackson2ObjectMapperBuilder.json();
    gatewayRestObjectMapperCustomizer().accept(builder);
    return builder.build();
  }
}
