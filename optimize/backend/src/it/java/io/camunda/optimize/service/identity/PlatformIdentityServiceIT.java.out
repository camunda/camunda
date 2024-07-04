/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.identity;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.optimize.GroupDto;
// import io.camunda.optimize.dto.optimize.UserDto;
// import java.util.Optional;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class PlatformIdentityServiceIT extends AbstractPlatformIT {
//
//   private PlatformIdentityService identityService;
//
//   @BeforeEach
//   public void setup() {
//     identityService = embeddedOptimizeExtension.getIdentityService();
//   }
//
//   @Test
//   public void getUserByIdFromCache() {
//     final UserDto userIdentity =
//         new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
//     identityService.addIdentity(userIdentity);
//     identityService.addIdentity(
//         new UserDto("otherId", "Frodo", "Baggins", "frodo.baggins@camunda.com"));
//
//     final Optional<UserDto> retrievedUserDto = identityService.getUserById("testUser");
//     assertThat(retrievedUserDto).isPresent().get().isEqualTo(userIdentity);
//   }
//
//   @Test
//   public void getGroupByIdFromCache() {
//     final GroupDto groupIdentity = new GroupDto("hobbits", "The Hobbits", 4L);
//     identityService.addIdentity(groupIdentity);
//     identityService.addIdentity(new GroupDto("orcs", "The Orcs", 1000L));
//
//     final Optional<GroupDto> retrievedUserDto = identityService.getGroupById("hobbits");
//     assertThat(retrievedUserDto).isPresent().get().isEqualTo(groupIdentity);
//   }
// }
