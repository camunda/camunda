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
package io.camunda.zeebe.protocol.record;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.camunda.zeebe.protocol.record.ImmutableProtocol.Builder;
import io.camunda.zeebe.protocol.record.ImmutableProtocol.Type;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;
import org.immutables.annotate.InjectAnnotation;
import org.immutables.annotate.InjectAnnotation.Where;
import org.immutables.value.Generated;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.ValidationMethod;

/**
 * Types annotated with this are considered concrete types of the protocol (e.g. {@link Record}, and
 * not simply behavioral types (e.g. {@link JsonSerializable}.
 *
 * <p>For every abstract type annotated with this, an immutable, concrete type will be generated.
 * For example, given {@link Record}, then {@link ImmutableRecord} will be generated. Every leaf
 * type of the protocol - that is, any type which may end up in {@link Record} or nested in one of
 * its properties - should be annotated with this.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Value.Style(
    // standardize to mimic Java beans
    get = {"is*", "get*"},
    // do not generate code which relies on Guava or other libraries
    jdkOnly = true,
    // do not pre-compute the hash, instead compute it once the first time it's required and memoize
    // it
    defaults = @Value.Immutable(lazyhash = true),
    // allow null values to be passed; this allows for partial deserialization
    validationMethod = ValidationMethod.NONE,
    clearBuilder = true,
    // specify annotations that can be passed to adding/removing new annotations due to direct or
    // transitive dependency changes
    allowedClasspathAnnotations = {
      Generated.class,
      ParametersAreNonnullByDefault.class,
      Immutable.class,
      SuppressFBWarnings.class,
      NotThreadSafe.class,
      Nullable.class
    },
    // enables passing other immutable builders as arguments, allowing you to chain multiple
    // builders together which may help reduce the amount of allocations
    attributeBuilderDetection = true,
    // enables detecting the presence of types that also have immutable counterparts; while this
    // slows down compilation, it enables us to do a deep copy of objects
    deepImmutablesDetection = true,
    // disable the Jackson integration to avoid bringing this in with the protocol; this is later
    // bridged in the protocol-jackson module
    jacksonIntegration = false,
    // converts methods for attributes such as `jobs` from `addJobs(JobRecordValue)` to
    // `addJob(JobRecordValue)` for the variants with only one argument
    depluralize = true,
    // prefix builder methods with `with` to distinguish them from other, non attribute methods
    // present in the builder; this will be useful to integrate with Jackson in protocol-jackson,
    // for example
    init = "with*")
@InjectAnnotation(
    target = Where.BUILDER_TYPE,
    type = Builder.class) // mark inner builder as part of immutable protocol
@InjectAnnotation(
    target = Where.IMMUTABLE_TYPE,
    type = Type.class,
    code = "(builder=[[builder]])") // mark generated class as part of immutable protocol
public @interface ImmutableProtocol {

  /** Returns the builder class for this abstract protocol type */
  Class<?> builder();

  /** Marker annotation to indicate that a class is an immutable protocol type */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @interface Type {
    Class<?> builder();
  }

  /** Marker annotation to indicate that a class is an immutable protocol type builder */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @interface Builder {}
}
