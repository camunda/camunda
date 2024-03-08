package io.camunda.zeebe.spring.client;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.common.json.SdkObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import io.camunda.zeebe.spring.client.configuration.*;
import io.camunda.zeebe.spring.client.event.ZeebeLifecycleEventProducer;
import io.camunda.zeebe.spring.client.testsupport.SpringZeebeTestContext;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Enabled by META-INF of Spring Boot Starter to provide beans for Camunda Clients */
@Configuration
@ImportAutoConfiguration({
  ZeebeClientProdAutoConfiguration.class,
  ZeebeClientAllAutoConfiguration.class,
  CommonClientConfiguration.class,
  ZeebeActuatorConfiguration.class,
  MetricsDefaultConfiguration.class
})
@AutoConfigureAfter(
    JacksonAutoConfiguration
        .class) // make sure Spring created ObjectMapper is preferred if available
public class CamundaAutoConfiguration {

  public static final ObjectMapper DEFAULT_OBJECT_MAPPER =
      new ObjectMapper()
          .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
          .configure(ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Bean
  @ConditionalOnMissingBean(
      SpringZeebeTestContext
          .class) // only run if we are not running in a test case - as otherwise the the lifecycle
  // is controlled by the test
  public ZeebeLifecycleEventProducer zeebeLifecycleEventProducer(
      final ZeebeClient client, final ApplicationEventPublisher publisher) {
    return new ZeebeLifecycleEventProducer(client, publisher);
  }

  /**
   * Registering a JsonMapper bean when there is none already exists in {@link
   * org.springframework.beans.factory.BeanFactory}.
   *
   * <p>NOTE: This method SHOULD NOT be explicitly called as it might lead to unexpected behaviour
   * due to the {@link ConditionalOnMissingBean} annotation. i.e. Calling this method when another
   * JsonMapper bean is defined in the context might throw {@link
   * org.springframework.beans.factory.NoSuchBeanDefinitionException}
   *
   * @return a new JsonMapper bean if none already exists in {@link
   *     org.springframework.beans.factory.BeanFactory}
   */
  @Bean(name = "zeebeJsonMapper")
  @ConditionalOnMissingBean
  public JsonMapper jsonMapper(final ObjectMapper objectMapper) {
    return new ZeebeObjectMapper(objectMapper);
  }

  @Bean(name = "commonJsonMapper")
  @ConditionalOnMissingBean
  public io.camunda.common.json.JsonMapper commonJsonMapper(final ObjectMapper objectMapper) {
    return new SdkObjectMapper(objectMapper);
  }
}
