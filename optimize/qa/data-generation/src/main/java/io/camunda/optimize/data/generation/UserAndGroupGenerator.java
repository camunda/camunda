/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.data.generation;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.rest.engine.dto.EngineUserDto;
import io.camunda.optimize.rest.engine.dto.GroupDto;
import io.camunda.optimize.rest.engine.dto.UserCredentialsDto;
import io.camunda.optimize.rest.engine.dto.UserProfileDto;
import io.camunda.optimize.test.util.client.SimpleEngineClient;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;

@AllArgsConstructor
public class UserAndGroupGenerator implements UserAndGroupProvider {
  private final SimpleEngineClient engineClient;
  @Getter private final List<UserProfileDto> users;
  private final List<GroupDto> groups;

  @SneakyThrows
  public UserAndGroupGenerator(final SimpleEngineClient engineClient) {
    this.engineClient = engineClient;
    users =
        IOUtils.readLines(
                Objects.requireNonNull(
                    UserAndGroupGenerator.class.getResourceAsStream("/users.csv")),
                StandardCharsets.UTF_8)
            .stream()
            .map(rawUserLine -> rawUserLine.split(","))
            .map(
                properties ->
                    UserProfileDto.builder()
                        .id(properties[3])
                        .firstName(properties[1])
                        .lastName(properties[0])
                        .email(properties[2])
                        .build())
            .collect(ImmutableList.toImmutableList());
    groups =
        IOUtils.readLines(
                Objects.requireNonNull(
                    UserAndGroupGenerator.class.getResourceAsStream("/groups.csv")),
                StandardCharsets.UTF_8)
            .stream()
            .map(rawGroupLine -> rawGroupLine.split(","))
            .map(
                properties ->
                    GroupDto.builder()
                        .id(properties[0])
                        .name(properties[1])
                        .type(properties[2])
                        .build())
            .collect(ImmutableList.toImmutableList());
  }

  @SneakyThrows
  public void generateUsers() {
    users.stream()
        .parallel()
        .forEach(
            userProfileDto -> {
              engineClient.createUser(
                  new EngineUserDto(
                      userProfileDto, new UserCredentialsDto(userProfileDto.getId())));
              engineClient.grantUserOptimizeAllDefinitionAndTenantsAndIdentitiesAuthorization(
                  userProfileDto.getId());
            });
  }

  @Override
  public String getRandomUserId() {
    return users.get(ThreadLocalRandom.current().nextInt(limitedRandomEntryIndex(users))).getId();
  }

  @SneakyThrows
  public void generateGroups() {
    groups.stream().parallel().forEach(engineClient::createGroup);
  }

  @Override
  public String getRandomGroupId() {
    return groups.get(ThreadLocalRandom.current().nextInt(limitedRandomEntryIndex(groups))).getId();
  }

  private int limitedRandomEntryIndex(final Collection<?> sourceCollection) {
    return Math.min(sourceCollection.size(), 25);
  }
}
