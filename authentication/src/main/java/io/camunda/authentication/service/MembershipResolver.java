/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import java.util.List;

/**
 * Looks up the membership fields of a single authenticated principal. Each accessor is expected to
 * memoize so concurrent or repeated reads share work; callers wire these as the {@code *Supplier}
 * arguments on the {@code CamundaAuthentication} builder.
 */
public interface MembershipResolver {

  List<String> groups();

  List<String> roles();

  List<String> tenants();

  List<String> mappingRules();
}
