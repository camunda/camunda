/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.console;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import java.util.List;
import org.springframework.web.client.RestTemplate;

public class ConsoleClient {

  private static final String MIGRATION_DATA_ENDPOINT =
      "/external/organizations/{0}/clusters/{1}/migrationData/{2}";

  private final IdentityMigrationProperties properties;
  private final RestTemplate restTemplate;

  public ConsoleClient(
      final IdentityMigrationProperties properties, final RestTemplate restTemplate) {
    this.properties = properties;
    this.restTemplate = restTemplate;
  }

  public Members fetchMembers() {
    return restTemplate.getForObject(
        MIGRATION_DATA_ENDPOINT,
        Members.class,
        properties.getOrganizationId(),
        properties.getConsole().getClusterId(),
        properties.getConsole().getInternalClientId());
  }

  public record Members(List<Member> members, List<Client> clients) {}

  public record Client(String name, String clientId, List<Permission> permissions) {}

  public record Member(String originalUserId, List<Role> roles, String email, String name) {}

  public enum Permission {
    ZEEBE("Zeebe"),
    OPERATE("Operate"),
    TASKLIST("Tasklist"),
    OPTIMIZE("Optimize"),
    SECRETS("Secrets");

    private final String name;

    Permission(final String name) {
      this.name = name;
    }

    @JsonValue
    public String getName() {
      return name;
    }

    @JsonCreator
    public static Permission fromValue(final String value) {
      for (final Permission permission : Permission.values()) {
        if (permission.name.equalsIgnoreCase(value)) {
          return permission;
        }
      }
      throw new IllegalArgumentException("Unknown permission: " + value);
    }
  }

  public enum Role {
    MEMBER("member"),
    ADMIN("admin"),
    ORGANIZATION_ADMIN("organizationadmin"),
    ORGANIZATION_OWNER("organizationowner"),
    OWNER("owner"),
    SUPPORT_AGENT("supportagent"),
    OPERATIONS_ENGINEER("operationsengineer"),
    TASK_USER("taskuser"),
    ANALYST("analyst"),
    DEVELOPER("developer"),
    VISITOR("visitor"),
    MODELER("modeler");

    private final String name;

    Role(final String name) {
      this.name = name;
    }

    @JsonValue
    public String getName() {
      return name;
    }

    @JsonCreator
    public static Role fromValue(final String value) {
      for (final Role role : Role.values()) {
        if (role.name.equalsIgnoreCase(value)) {
          return role;
        }
      }
      throw new IllegalArgumentException("Unknown role: " + value);
    }
  }
}
