/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation;

import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.camunda.optimize.rest.engine.dto.EngineUserDto;
import org.camunda.optimize.rest.engine.dto.GroupDto;
import org.camunda.optimize.rest.engine.dto.UserCredentialsDto;
import org.camunda.optimize.rest.engine.dto.UserProfileDto;
import org.camunda.optimize.test.util.client.SimpleEngineClient;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@AllArgsConstructor
public class UserAndGroupGenerator implements UserAndGroupProvider {
  private final SimpleEngineClient engineClient;
  @Getter
  private final List<UserProfileDto> users;
  private final List<GroupDto> groups;

  @SneakyThrows
  public UserAndGroupGenerator(final SimpleEngineClient engineClient) {
    this.engineClient = engineClient;
    this.users = IOUtils.readLines(getClass().getResourceAsStream("/users.csv"), StandardCharsets.UTF_8)
      .stream()
      .map(rawUserLine -> rawUserLine.split(","))
      .map(properties -> UserProfileDto.builder()
        .id(properties[3])
        .firstName(properties[1])
        .lastName(properties[0])
        .email(properties[2])
        .build()
      )
      .collect(ImmutableList.toImmutableList());
    this.groups = IOUtils.readLines(getClass().getResourceAsStream("/groups.csv"), StandardCharsets.UTF_8)
      .stream()
      .map(rawGroupLine -> rawGroupLine.split(","))
      .map(properties -> GroupDto.builder().id(properties[0]).name(properties[1]).type(properties[2]).build())
      .collect(ImmutableList.toImmutableList());
  }

  @SneakyThrows
  public void generateUsers() {
    users.stream().parallel()
      .forEach(userProfileDto -> {
        engineClient.createUser(new EngineUserDto(userProfileDto, new UserCredentialsDto(userProfileDto.getId())));
        engineClient.grantUserOptimizeAllDefinitionAndTenantsAndIdentitiesAuthorization(userProfileDto.getId());
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
