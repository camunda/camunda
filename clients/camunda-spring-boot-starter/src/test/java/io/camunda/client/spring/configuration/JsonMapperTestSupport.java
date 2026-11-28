/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.spring.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.protocol.rest.StringFilterProperty;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

/**
 * Shared fixtures for the tests that pin which {@link JsonMapper} the starter selects, and how it
 * serializes, for each combination of Spring Boot version, Jackson version and user-declared beans.
 *
 * <p>Every setup boots a real application context through {@link EnableAutoConfiguration}, i.e.
 * through the {@code META-INF/spring/...AutoConfiguration.imports} of the module under test. This
 * is deliberate: the selection depends on the order in which auto-configurations are evaluated, and
 * that order is only applied on this path. Listing configuration classes explicitly (as in
 * {@code @SpringBootTest(classes = ...)} or {@code AutoConfigurations.of(...)}) does not reproduce
 * it.
 *
 * <p>These sources are compiled and run by both the Spring Boot 4 starter and the Spring Boot 3
 * starter, so they must not reference version specific classes: Spring Boot and Jackson 3 classes
 * are only referred to by name. Setups that cannot exist on the running classpath report so through
 * {@link JsonMapperSetup#isAvailable()} and are skipped.
 */
final class JsonMapperTestSupport {

  static final String BOOT_3_JACKSON_AUTO_CONFIGURATION =
      "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration";
  static final String BOOT_4_JACKSON_2_AUTO_CONFIGURATION =
      "org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration";
  static final String BOOT_4_JACKSON_3_AUTO_CONFIGURATION =
      "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration";

  /**
   * The same Spring Jackson settings under the property prefixes of every supported Spring Boot
   * version: {@code spring.jackson.*} configures the Jackson 2 mapper on Spring Boot 3 and the
   * Jackson 3 mapper on Spring Boot 4, {@code spring.jackson2.*} configures the Jackson 2 mapper on
   * Spring Boot 4. Prefixes unknown to the running Spring Boot version are ignored.
   */
  static final String[] SPRING_JACKSON_PROPERTIES = {
    "spring.jackson.property-naming-strategy=SNAKE_CASE",
    "spring.jackson.default-property-inclusion=non_null",
    "spring.jackson2.property-naming-strategy=SNAKE_CASE",
    "spring.jackson2.default-property-inclusion=non_null"
  };

  /**
   * Removes Spring Boot 4's Jackson 2 auto-configuration, which is what an application without
   * {@code spring-boot-jackson2} on its classpath amounts to. The class itself cannot be hidden
   * from the class loader instead: it would still be listed in the {@code .imports} file of its
   * jar.
   */
  private static final String EXCLUDE_JACKSON_2 =
      "spring.autoconfigure.exclude=" + BOOT_4_JACKSON_2_AUTO_CONFIGURATION;

  private static final String EXCLUDE_ALL_SPRING_JACKSON =
      "spring.autoconfigure.exclude="
          + String.join(
              ",",
              BOOT_3_JACKSON_AUTO_CONFIGURATION,
              BOOT_4_JACKSON_2_AUTO_CONFIGURATION,
              BOOT_4_JACKSON_3_AUTO_CONFIGURATION);

  private static final String[] SPRING_JACKSON_PROPERTIES_WITHOUT_JACKSON_2 =
      Stream.concat(Stream.of(SPRING_JACKSON_PROPERTIES), Stream.of(EXCLUDE_JACKSON_2))
          .toArray(String[]::new);

  private JsonMapperTestSupport() {}

  /** Variables every setup can serialize: no {@code java.time} and no {@code Optional}. */
  static Map<String, Object> plainVariables() {
    final Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("order", new Order("A-1", 3, null));
    variables.put("empty", new Object());
    variables.put("filter", exactMatchFilter());
    return variables;
  }

  /** Variables with {@code java.time} types, as sent by applications that use dates and times. */
  static Map<String, Object> javaTimeVariables() {
    final Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("date", LocalDate.of(1990, 1, 1));
    variables.put("duration", Duration.ofMinutes(90));
    variables.put("instant", Instant.parse("2024-02-29T10:15:30Z"));
    return variables;
  }

  /** A Camunda DTO that is serialized through a custom module of {@link CamundaObjectMapper}. */
  static StringFilterProperty exactMatchFilter() {
    final StringFilterProperty filter = new StringFilterProperty();
    filter.set$Eq("admin");
    return filter;
  }

  /**
   * Serializes the value, or describes why this was not possible (as {@code ERROR <exception
   * chain>}), so that setups that cannot serialize a type are pinned like any other output.
   */
  static String serializeOrDescribeFailure(final JsonMapper jsonMapper, final Object value) {
    try {
      return jsonMapper.toJson(value);
    } catch (final RuntimeException e) {
      final StringBuilder chain = new StringBuilder("ERROR ").append(e.getClass().getSimpleName());
      for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
        chain.append(" <- ").append(cause.getClass().getSimpleName());
      }
      return chain.toString();
    }
  }

  /** The simple name of the configuration class that registered the {@code JsonMapper} bean. */
  static String selectedBy(final AssertableApplicationContext context) {
    final String[] beanNames = context.getBeanNamesForType(JsonMapper.class);
    if (beanNames.length != 1) {
      throw new AssertionError(
          "expected exactly one JsonMapper, got " + Arrays.toString(beanNames));
    }
    final String factoryBeanName =
        context.getBeanFactory().getBeanDefinition(beanNames[0]).getFactoryBeanName();
    return factoryBeanName.substring(factoryBeanName.lastIndexOf('.') + 1);
  }

  static boolean isPresent(final String className) {
    return ClassUtils.isPresent(className, JsonMapperTestSupport.class.getClassLoader());
  }

  /** Immutable payload object, serialized with the naming strategy of the mapper. */
  public record Order(String orderId, int itemCount, String note) {}

  /** The application: nothing but auto-configuration, like an application without own mappers. */
  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class AutoConfigurationApplication {}

  /** An application that declares its own Jackson 2 {@code ObjectMapper}. */
  @Configuration(proxyBeanMethods = false)
  static class UserObjectMapperConfiguration {
    @Bean
    ObjectMapper userObjectMapper() {
      return new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
          .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
    }
  }

  /** An application that declares its own Camunda {@code JsonMapper}. */
  @Configuration(proxyBeanMethods = false)
  static class UserJsonMapperConfiguration {
    @Bean
    JsonMapper userJsonMapper() {
      return new CamundaObjectMapper(
          new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE));
    }
  }

  /** A classpath / bean setup an application can be in. */
  enum JsonMapperSetup {
    /**
     * Spring Boot's Jackson 2 auto-configuration is the only Jackson auto-configuration: Spring
     * Boot 3, or Spring Boot 4 with {@code spring-boot-jackson2} and without Jackson 3.
     */
    SPRING_JACKSON_2_ONLY(new String[0], "tools.jackson") {
      @Override
      boolean isAvailable() {
        return isPresent(BOOT_3_JACKSON_AUTO_CONFIGURATION)
            || isPresent(BOOT_4_JACKSON_2_AUTO_CONFIGURATION);
      }
    },
    /** As {@link #SPRING_JACKSON_2_ONLY}, with Spring Jackson settings. */
    SPRING_JACKSON_2_ONLY_CONFIGURED(SPRING_JACKSON_PROPERTIES, "tools.jackson") {
      @Override
      boolean isAvailable() {
        return SPRING_JACKSON_2_ONLY.isAvailable();
      }
    },
    /**
     * Spring Boot 4 with both the Jackson 3 and the Jackson 2 (compatibility) auto-configuration.
     */
    SPRING_BOOT_4_JACKSON_2_AND_3(new String[0]) {
      @Override
      boolean isAvailable() {
        return isPresent(BOOT_4_JACKSON_2_AUTO_CONFIGURATION)
            && isPresent(BOOT_4_JACKSON_3_AUTO_CONFIGURATION);
      }
    },
    /** As {@link #SPRING_BOOT_4_JACKSON_2_AND_3}, with Spring Jackson settings. */
    SPRING_BOOT_4_JACKSON_2_AND_3_CONFIGURED(SPRING_JACKSON_PROPERTIES) {
      @Override
      boolean isAvailable() {
        return SPRING_BOOT_4_JACKSON_2_AND_3.isAvailable();
      }
    },
    /**
     * Spring Boot 4 default: only the Jackson 3 auto-configuration, no {@code
     * spring-boot-jackson2}.
     */
    SPRING_BOOT_4_JACKSON_3_ONLY(new String[] {EXCLUDE_JACKSON_2}) {
      @Override
      boolean isAvailable() {
        return isPresent(BOOT_4_JACKSON_3_AUTO_CONFIGURATION);
      }
    },
    /** As {@link #SPRING_BOOT_4_JACKSON_3_ONLY}, with Spring Jackson settings. */
    SPRING_BOOT_4_JACKSON_3_ONLY_CONFIGURED(SPRING_JACKSON_PROPERTIES_WITHOUT_JACKSON_2) {
      @Override
      boolean isAvailable() {
        return SPRING_BOOT_4_JACKSON_3_ONLY.isAvailable();
      }
    },
    /**
     * Without any Jackson auto-configuration of Spring Boot, e.g. an application that only has
     * {@code spring-boot-starter} and the Camunda starter on the classpath. There is no Spring
     * mapper to use.
     */
    NO_SPRING_JACKSON(new String[] {EXCLUDE_ALL_SPRING_JACKSON}),
    /**
     * The application declares a Jackson 2 {@code ObjectMapper} bean (on the default classpath).
     */
    USER_OBJECT_MAPPER(SPRING_JACKSON_PROPERTIES) {
      @Override
      Class<?> userConfiguration() {
        return UserObjectMapperConfiguration.class;
      }
    },
    /** As {@link #USER_OBJECT_MAPPER}, on a Spring Boot 4 classpath without Jackson 2 support. */
    USER_OBJECT_MAPPER_SPRING_BOOT_4_JACKSON_3_ONLY(SPRING_JACKSON_PROPERTIES_WITHOUT_JACKSON_2) {
      @Override
      boolean isAvailable() {
        return SPRING_BOOT_4_JACKSON_3_ONLY.isAvailable();
      }

      @Override
      Class<?> userConfiguration() {
        return UserObjectMapperConfiguration.class;
      }
    },
    /** The application declares a Camunda {@code JsonMapper} bean (on the default classpath). */
    USER_JSON_MAPPER(SPRING_JACKSON_PROPERTIES) {
      @Override
      Class<?> userConfiguration() {
        return UserJsonMapperConfiguration.class;
      }
    };

    private final String[] properties;
    private final String[] hiddenPackages;

    JsonMapperSetup(final String[] properties, final String... hiddenPackages) {
      this.properties = properties;
      this.hiddenPackages = hiddenPackages;
    }

    /** Whether this setup can be built from the classpath the tests run with. */
    boolean isAvailable() {
      return true;
    }

    Class<?> userConfiguration() {
      return AutoConfigurationApplication.class;
    }

    /**
     * Builds the runner. Jackson 3 classes are hidden from the class loader where the setup stands
     * for an application without Jackson 3, as the test classpath has both.
     */
    ApplicationContextRunner runner() {
      ApplicationContextRunner runner =
          new ApplicationContextRunner()
              .withUserConfiguration(AutoConfigurationApplication.class)
              .withPropertyValues(properties);
      if (userConfiguration() != AutoConfigurationApplication.class) {
        runner = runner.withUserConfiguration(userConfiguration());
      }
      if (hiddenPackages.length > 0) {
        runner = runner.withClassLoader(new FilteredClassLoader(hiddenPackages));
      }
      return runner;
    }
  }
}
