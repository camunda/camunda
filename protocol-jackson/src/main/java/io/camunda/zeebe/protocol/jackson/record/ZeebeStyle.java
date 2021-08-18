/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson.record;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.BuilderVisibility;
import org.immutables.value.Value.Style.ImplementationVisibility;
import org.immutables.value.Value.Style.ValidationMethod;

@Target({ElementType.PACKAGE, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
@Value.Style(
    // standardize to mimic Java beans
    get = {"is*", "get*"},
    // hide the implementation class to reduce the API surface, instead only exposing the builder
    // and abstract classes
    visibility = ImplementationVisibility.PACKAGE,
    builderVisibility = BuilderVisibility.PUBLIC,
    implementationNestedInBuilder = true,
    overshadowImplementation = true,
    // do not generate code which relies on Guava or other libraries
    jdkOnly = true,
    // do not pre-compute the hash, instead compute it once the first time it's required and memoize
    // it; further, disable generation of copy methods which will only target the `Abstract*` type
    // and not the interface type, as these become rather pointless
    defaults = @Value.Immutable(lazyhash = true, copy = false),
    validationMethod = ValidationMethod.NONE,
    headerComments = true,
    clearBuilder = true,
    attributeBuilderDetection = true)
@JsonSerialize
@interface ZeebeStyle {}
