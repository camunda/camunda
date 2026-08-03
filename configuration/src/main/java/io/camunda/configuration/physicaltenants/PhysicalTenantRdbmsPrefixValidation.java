/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Table-prefix policy for RDBMS secondary storage: a configured prefix must be usable as an
 * unquoted SQL identifier prefix, which on the supported dialects means upper case.
 *
 * <p>The prefix is interpolated verbatim into DDL and DML identifiers by the Liquibase changelog
 * and the MyBatis mappers. A mixed-case prefix splits into two different relations: the structured
 * {@code <createTable>} quotes the resulting name and stores it case-preserved, while the raw-SQL
 * and mapper references are unquoted and fold to lower case. The boot-time migration then aborts
 * part-way with a {@code relation "<prefix>process_instance" does not exist} on a table it just
 * created. Rejecting the prefix up front turns that into a configuration error.
 *
 * <p>Enforcement is <em>key inspection plus a single bind</em> per declared prefix property: the
 * root {@code camunda.data.secondary-storage.rdbms.prefix} and the per-tenant override under each
 * discovered {@code camunda.physical-tenants.<id>.}. Checking each declared key on its own is
 * sufficient — a tenant that declares no prefix inherits the root value, which is validated at its
 * own key.
 */
@NullMarked
final class PhysicalTenantRdbmsPrefixValidation {

  /** The prefix property, expressed relative to a configuration root. */
  private static final String PREFIX_PROPERTY = "data.secondary-storage.rdbms.prefix";

  /**
   * An unquoted SQL identifier prefix: upper-case letters, digits and underscores, not starting
   * with a digit. Trailing separators such as {@code _} are the conventional form ({@code TA_}).
   */
  private static final Pattern VALID_PREFIX = Pattern.compile("[A-Z_][A-Z0-9_]*");

  private PhysicalTenantRdbmsPrefixValidation() {}

  static void validate(final Environment environment, final Set<String> physicalTenantIds) {
    final Binder binder = Binder.get(environment);
    final List<String> violations = new ArrayList<>();
    for (final String propertyName : prefixProperties(physicalTenantIds)) {
      binder
          .bind(propertyName, Bindable.of(String.class))
          .ifBound(
              prefix -> {
                if (!prefix.isBlank() && !VALID_PREFIX.matcher(prefix.trim()).matches()) {
                  violations.add(propertyName + "=" + prefix);
                }
              });
    }
    if (!violations.isEmpty()) {
      throw new UnifiedConfigurationException(
          "The RDBMS table prefix is interpolated verbatim into unquoted SQL identifiers, so it must "
              + "contain only upper-case letters, digits and underscores, and must not start with a "
              + "digit. A lower-case or mixed-case prefix creates case-quoted tables that the "
              + "unquoted queries and migrations cannot address, which aborts the schema migration "
              + "at boot. Invalid prefixes: "
              + violations);
    }
  }

  private static List<String> prefixProperties(final Set<String> physicalTenantIds) {
    final List<String> propertyNames = new ArrayList<>();
    propertyNames.add(Camunda.PREFIX + "." + PREFIX_PROPERTY);
    physicalTenantIds.forEach(
        id ->
            propertyNames.add(
                PhysicalTenantResolver.PHYSICAL_TENANTS_PREFIX + "." + id + "." + PREFIX_PROPERTY));
    return propertyNames;
  }
}
