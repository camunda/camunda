/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.camunda.bpm.engine.rest.dto.identity.UserCredentialsDto;
import org.camunda.bpm.engine.rest.dto.identity.UserDto;
import org.camunda.bpm.engine.rest.dto.identity.UserProfileDto;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

@AllArgsConstructor
public class UserGenerator {
  private final SimpleEngineClient engineClient;

  @SneakyThrows
  public void generateUsers() {
    final List<String> lines = IOUtils.readLines(
      getClass().getResourceAsStream("/users.csv"), StandardCharsets.UTF_8
    );
    lines.stream().parallel()
      .forEach(rawUserLine -> {
        final String[] properties = rawUserLine.split(",");

        final UserDto engineUserDto = new UserDto();
        final UserProfileDto userProfileDto = new UserProfileDto();
        engineUserDto.setProfile(userProfileDto);
        userProfileDto.setFirstName(properties[1]);
        userProfileDto.setLastName(properties[0]);
        userProfileDto.setEmail(properties[2]);
        userProfileDto.setId(properties[3]);
        final UserCredentialsDto userCredentialsDto = new UserCredentialsDto();
        engineUserDto.setCredentials(userCredentialsDto);
        userCredentialsDto.setPassword(userProfileDto.getId());

        engineClient.createUser(engineUserDto);
        engineClient.grantUserOptimizeAndAllDefinitionAuthorization(engineUserDto.getProfile().getId());
      });
  }

}
