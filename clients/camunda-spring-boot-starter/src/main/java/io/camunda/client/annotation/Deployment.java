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

import io.camunda.client.annotation.Deployment.Deployments;
import java.lang.annotation.*;

@Repeatable(Deployments.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited // has to be inherited to work on spring aop beans
public @interface Deployment {

  /**
   * The resource that should be deployed from this annotation. Supports spring resource pattern
   * resolver mechanisms.
   */
  String[] resources() default {};

  /** The tenant id this deployment should use. */
  String tenantId() default "";

  /**
   * Restricts deployment to resources that are packaged in the same JAR as the annotated class.
   * This is useful in multi-module or multi-JAR projects to ensure that each module only deploys
   * its own resources.
   *
   * <p>Can only be used as a singleton. Although this is declared as a {@code boolean[]} to support
   * repeatable annotations, each {@link Deployment} should specify at most a single value; if
   * multiple values are provided, the processing of the annotation at runtime will fail.
   */
  boolean[] ownJarOnly() default {};

  @Documented
  @Inherited
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface Deployments {
    Deployment[] value();
  }
}
