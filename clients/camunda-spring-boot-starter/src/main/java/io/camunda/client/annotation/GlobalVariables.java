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
package io.camunda.client.annotation;

import io.camunda.client.annotation.GlobalVariables.GlobalVariablesContainer;
import java.lang.annotation.*;

/**
 * Annotation to define global variables for Camunda Client operations. Can be applied at both class
 * and method levels.
 *
 * <p>When applied at the class level, the resources added as parameter are loaded.
 *
 * <p>When applied at method level, the method is invoked to produce the according variables.
 */
@Repeatable(GlobalVariablesContainer.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited // has to be inherited to work on spring aop beans
public @interface GlobalVariables {

  /**
   * JSON resource files to load global variables from. Supports Spring resource pattern resolver
   * mechanisms (e.g., {@code "classpath:global-variables.json"}).
   *
   * <p>Each JSON file should contain a flat JSON object where each key becomes a variable name and
   * the corresponding value becomes the variable value.
   *
   * <p>Only effective on class level.
   */
  String[] resources() default {};

  /** The tenant id for tenant-scoped cluster variables. */
  String tenantId() default "";

  @Documented
  @Inherited
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE, ElementType.METHOD})
  public @interface GlobalVariablesContainer {
    GlobalVariables[] value();
  }
}
