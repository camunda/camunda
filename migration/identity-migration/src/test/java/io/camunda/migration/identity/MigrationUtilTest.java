/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static io.camunda.migration.identity.MigrationUtil.normalizeGroupID;
import static io.camunda.migration.identity.MigrationUtil.normalizeID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.identity.config.EntitiesProperties;
import io.camunda.migration.identity.config.EntitiesProperties.EntityType;
import io.camunda.migration.identity.config.EntitiesProperties.NormalizationConfig;
import io.camunda.migration.identity.dto.Group;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MigrationUtilTest {

  @Nested
  class NormalizeIDTest {

    @Test
    void shouldApplyLowercaseByDefault() {
      // given
      final var properties = new EntitiesProperties();
      final String input = "MyRoleName";

      // when
      final String result = normalizeID(input, properties, EntityType.ROLE);

      // then
      assertThat(result).isEqualTo("myrolename");
    }

    @Test
    void shouldNotApplyLowercaseWhenDisabledInDefaults() {
      // given
      final var properties = new EntitiesProperties();
      properties.getDefaults().setLowercase(false);
      properties.getDefaults().setPattern("[^a-zA-Z0-9_@.-]"); // Allow uppercase
      final String input = "MyRoleName";

      // when
      final String result = normalizeID(input, properties, EntityType.ROLE);

      // then
      assertThat(result).isEqualTo("MyRoleName");
    }

    @Test
    void shouldApplyEntitySpecificLowercaseConfig() {
      // given
      final var properties = new EntitiesProperties();
      properties.getDefaults().setLowercase(true);
      properties.getRole().setLowercase(false);
      properties.getRole().setPattern("[^a-zA-Z0-9_@.-]"); // Allow uppercase for roles
      final String input = "MyRoleName";

      // when
      final String roleResult = normalizeID(input, properties, EntityType.ROLE);
      final String groupResult = normalizeID(input, properties, EntityType.GROUP);

      // then
      assertThat(roleResult).isEqualTo("MyRoleName");
      assertThat(groupResult).isEqualTo("myrolename");
    }

    @Test
    void shouldReplaceDisallowedCharactersWithUnderscore() {
      // given
      final var properties = new EntitiesProperties();
      final String input = "role@name#with$special%chars";

      // when
      final String result = normalizeID(input, properties, EntityType.ROLE);

      // then
      assertThat(result).isEqualTo("role@name_with_special_chars");
    }

    @Test
    void shouldAllowValidCharacters() {
      // given
      final var properties = new EntitiesProperties();
      final String input = "valid-role.name_123@example.com";

      // when
      final String result = normalizeID(input, properties, EntityType.ROLE);

      // then
      assertThat(result).isEqualTo("valid-role.name_123@example.com");
    }

    @Test
    void shouldApplyCustomPattern() {
      // given
      final var properties = new EntitiesProperties();
      properties.getDefaults().setPattern("[^a-z0-9]");
      final String input = "role-name.test@example";

      // when
      final String result = normalizeID(input, properties, EntityType.ROLE);

      // then
      assertThat(result).isEqualTo("role_name_test_example");
    }

    @Test
    void shouldApplyEntitySpecificPattern() {
      // given
      final var properties = new EntitiesProperties();
      properties.getDefaults().setPattern("[^a-z0-9_@.-]");
      properties.getRole().setPattern("[^a-z0-9_]");
      final String input = "role-name.test@example";

      // when
      final String roleResult = normalizeID(input, properties, EntityType.ROLE);
      final String groupResult = normalizeID(input, properties, EntityType.GROUP);

      // then
      assertThat(roleResult).isEqualTo("role_name_test_example");
      assertThat(groupResult).isEqualTo("role-name.test@example");
    }

    @Test
    void shouldTruncateLongIdsTo256Characters() {
      // given
      final var properties = new EntitiesProperties();
      final String input = "a".repeat(300);

      // when
      final String result = normalizeID(input, properties, EntityType.ROLE);

      // then
      assertThat(result).hasSize(256);
      assertThat(result).isEqualTo("a".repeat(256));
    }

    @Test
    void shouldHandleExactly256Characters() {
      // given
      final var properties = new EntitiesProperties();
      final String input = "a".repeat(256);

      // when
      final String result = normalizeID(input, properties, EntityType.ROLE);

      // then
      assertThat(result).hasSize(256);
      assertThat(result).isEqualTo(input);
    }

    @Test
    void shouldNotTruncateIdsUnder256Characters() {
      // given
      final var properties = new EntitiesProperties();
      final String input = "a".repeat(255);

      // when
      final String result = normalizeID(input, properties, EntityType.ROLE);

      // then
      assertThat(result).hasSize(255);
      assertThat(result).isEqualTo(input);
    }

    @Test
    void shouldHandleEmptyString() {
      // given
      final var properties = new EntitiesProperties();
      final String input = "";

      // when
      final String result = normalizeID(input, properties, EntityType.ROLE);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldApplyCombinedTransformations() {
      // given
      final var properties = new EntitiesProperties();
      properties.getRole().setLowercase(false);
      properties.getRole().setPattern("[^a-zA-Z0-9_@.-]"); // Allow uppercase
      final String input = "Role@Name#123";

      // when
      final String result = normalizeID(input, properties, EntityType.ROLE);

      // then
      assertThat(result).isEqualTo("Role@Name_123");
    }

    @Test
    void shouldWorkWithAllEntityTypes() {
      // given
      final var properties = new EntitiesProperties();
      final String input = "Test@Entity#Name";

      // when & then
      assertThat(normalizeID(input, properties, EntityType.ROLE)).isEqualTo("test@entity_name");
      assertThat(normalizeID(input, properties, EntityType.GROUP)).isEqualTo("test@entity_name");
      assertThat(normalizeID(input, properties, EntityType.USER)).isEqualTo("test@entity_name");
      assertThat(normalizeID(input, properties, EntityType.MAPPING_RULE))
          .isEqualTo("test@entity_name");
    }

    @Test
    void shouldRespectIndependentEntityConfigs() {
      // given
      final var properties = new EntitiesProperties();
      properties.getRole().setLowercase(false);
      properties.getRole().setPattern("[^a-zA-Z0-9_@.-]"); // Allow uppercase for roles
      properties.getGroup().setLowercase(true);
      properties.getMappingRule().setPattern("[^a-z0-9]");
      final String input = "Test-Name@123";

      // when
      final String roleResult = normalizeID(input, properties, EntityType.ROLE);
      final String groupResult = normalizeID(input, properties, EntityType.GROUP);
      final String mappingRuleResult = normalizeID(input, properties, EntityType.MAPPING_RULE);

      // then
      assertThat(roleResult).isEqualTo("Test-Name@123");
      assertThat(groupResult).isEqualTo("test-name@123");
      assertThat(mappingRuleResult).isEqualTo("test_name_123");
    }

    @Test
    void shouldHandleUnicodeCharacters() {
      // given
      final var properties = new EntitiesProperties();
      final String input = "rôle-名前";

      // when
      final String result = normalizeID(input, properties, EntityType.ROLE);

      // then
      // Unicode characters are not in the allowed pattern, so they become underscores
      assertThat(result).isEqualTo("r_le-__");
    }

    @Test
    void shouldHandleMultipleConsecutiveSpecialCharacters() {
      // given
      final var properties = new EntitiesProperties();
      final String input = "role###name***test";

      // when
      final String result = normalizeID(input, properties, EntityType.ROLE);

      // then
      assertThat(result).isEqualTo("role___name___test");
    }
  }

  @Nested
  class NormalizeGroupIDTest {

    @Test
    void shouldNormalizeGroupNameWhenPresent() {
      // given
      final var properties = new EntitiesProperties();
      final var group = new Group("group-id", "Group@Name#123");

      // when
      final String result = normalizeGroupID(group, properties);

      // then
      assertThat(result).isEqualTo("group@name_123");
    }

    @Test
    void shouldFallbackToGroupIdWhenNameIsNull() {
      // given
      final var properties = new EntitiesProperties();
      final var group = new Group("fallback-id", null);

      // when
      final String result = normalizeGroupID(group, properties);

      // then
      assertThat(result).isEqualTo("fallback-id");
    }

    @Test
    void shouldFallbackToGroupIdWhenNameIsEmpty() {
      // given
      final var properties = new EntitiesProperties();
      final var group = new Group("fallback-id", "");

      // when
      final String result = normalizeGroupID(group, properties);

      // then
      assertThat(result).isEqualTo("fallback-id");
    }

    @Test
    void shouldApplyGroupSpecificConfig() {
      // given
      final var properties = new EntitiesProperties();
      properties.getGroup().setLowercase(false);
      properties.getGroup().setPattern("[^a-zA-Z0-9_@.-]"); // Allow uppercase
      final var group = new Group("group-id", "GroupName");

      // when
      final String result = normalizeGroupID(group, properties);

      // then
      assertThat(result).isEqualTo("GroupName");
    }

    @Test
    void shouldHandleLongGroupNames() {
      // given
      final var properties = new EntitiesProperties();
      final String longName = "Group-Name-".repeat(30); // Creates a name > 256 chars
      final var group = new Group("group-id", longName);

      // when
      final String result = normalizeGroupID(group, properties);

      // then
      assertThat(result).hasSize(256);
    }

    @Test
    void shouldApplyGroupPatternConfig() {
      // given
      final var properties = new EntitiesProperties();
      properties.getGroup().setPattern("[^a-z0-9_]");
      final var group = new Group("group-id", "Group-Name@Test");

      // when
      final String result = normalizeGroupID(group, properties);

      // then
      assertThat(result).isEqualTo("group_name_test");
    }
  }

  @Nested
  class NormalizationConfigTest {

    @Test
    void shouldUseDefaultsWhenEntityConfigIsNull() {
      // given
      final var properties = new EntitiesProperties();
      properties.getDefaults().setLowercase(false);
      properties.getDefaults().setPattern("[^a-z0-9]");
      // role config is not set, should inherit from defaults

      // when
      final NormalizationConfig config = properties.getEffectiveConfig(EntityType.ROLE);

      // then
      assertThat(config.isLowercaseEnabled()).isFalse();
      assertThat(config.getEffectivePattern()).isEqualTo("[^a-z0-9]");
    }

    @Test
    void shouldOverrideDefaultsWithEntitySpecificConfig() {
      // given
      final var properties = new EntitiesProperties();
      properties.getDefaults().setLowercase(true);
      properties.getDefaults().setPattern("[^a-z0-9_@.-]");
      properties.getRole().setLowercase(false);
      properties.getRole().setPattern("[^a-z0-9]");

      // when
      final NormalizationConfig config = properties.getEffectiveConfig(EntityType.ROLE);

      // then
      assertThat(config.isLowercaseEnabled()).isFalse();
      assertThat(config.getEffectivePattern()).isEqualTo("[^a-z0-9]");
    }

    @Test
    void shouldAllowPartialOverride() {
      // given
      final var properties = new EntitiesProperties();
      properties.getDefaults().setLowercase(true);
      properties.getDefaults().setPattern("[^a-z0-9_@.-]");
      properties.getRole().setLowercase(false);
      // pattern not set for role, should use default

      // when
      final NormalizationConfig config = properties.getEffectiveConfig(EntityType.ROLE);

      // then
      assertThat(config.isLowercaseEnabled()).isFalse();
      assertThat(config.getEffectivePattern()).isEqualTo("[^a-z0-9_@.-]");
    }

    @Test
    void shouldUseFallbacksWhenNotConfigured() {
      // given
      final var properties = new EntitiesProperties();

      // when
      final NormalizationConfig config = properties.getEffectiveConfig(EntityType.ROLE);

      // then - should use hardcoded fallbacks in NormalizationConfig
      assertThat(config.isLowercaseEnabled()).isTrue();
      assertThat(config.getEffectivePattern()).isEqualTo("[^a-z0-9_@.-]");
    }
  }

  @Nested
  class RealWorldScenarios {

    @Test
    void shouldHandleKeycloakRoleNameWithSpecialChars() {
      // given
      final var properties = new EntitiesProperties();
      final String roleName = "Identity/Admin Role";

      // when
      final String result = normalizeID(roleName, properties, EntityType.ROLE);

      // then
      assertThat(result).isEqualTo("identity_admin_role");
    }

    @Test
    void shouldHandleSaaSGroupNameWithSpaces() {
      // given
      final var properties = new EntitiesProperties();
      final var group = new Group("id", "Engineering Team 2024");

      // when
      final String result = normalizeGroupID(group, properties);

      // then
      assertThat(result).isEqualTo("engineering_team_2024");
    }

    @Test
    void shouldPreserveEmailLikeIdentifiers() {
      // given
      final var properties = new EntitiesProperties();
      final String email = "user@example.com";

      // when
      final String result = normalizeID(email, properties, EntityType.USER);

      // then
      assertThat(result).isEqualTo("user@example.com");
    }

    @Test
    void shouldHandleMixedCaseWithNumbersAndDots() {
      // given
      final var properties = new EntitiesProperties();
      final String mappingRule = "App.User.Role.v1.2.3";

      // when
      final String result = normalizeID(mappingRule, properties, EntityType.MAPPING_RULE);

      // then
      assertThat(result).isEqualTo("app.user.role.v1.2.3");
    }

    @Test
    void shouldAllowUsersToDisableLowercaseForRolesOnly() {
      // given - User wants to preserve role case but lowercase other entities
      final var properties = new EntitiesProperties();
      properties.getDefaults().setLowercase(true);
      properties.getRole().setLowercase(false);
      properties.getRole().setPattern("[^a-zA-Z0-9_@.-]"); // Must also allow uppercase in pattern!

      final String name = "MyEntity";

      // when
      final String roleResult = normalizeID(name, properties, EntityType.ROLE);
      final String groupResult = normalizeID(name, properties, EntityType.GROUP);

      // then
      assertThat(roleResult).isEqualTo("MyEntity");
      assertThat(groupResult).isEqualTo("myentity");
    }

    @Test
    void shouldHandleVeryLongGroupNameFromSaaS() {
      // given
      final var properties = new EntitiesProperties();
      // Create a string that's longer than 256 chars after normalization
      final String longName =
          "Group-Name-With-Many-Dashes-And-Spaces-".repeat(10); // 40 chars * 10 = 400 chars
      final var group = new Group("short-id", longName);

      // when
      final String result = normalizeGroupID(group, properties);

      // then
      assertThat(result).hasSize(256);
      assertThat(result).startsWith("group-name-with-many-dashes-and-spaces-");
    }
  }
}
