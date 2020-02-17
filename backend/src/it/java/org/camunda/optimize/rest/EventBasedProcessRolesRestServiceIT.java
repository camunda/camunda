/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableList;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessRoleDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessRoleRestDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;

public class EventBasedProcessRolesRestServiceIT extends AbstractEventProcessIT {
  private static final String USER_KERMIT = "kermit";
  private static final String TEST_GROUP = "testGroup";

  @Test
  public void createdEventBasedProcessContainsDefaultRole() {
    // given
    final EventProcessMappingDto eventProcessMappingDto = createEventProcessMappingDtoWithSimpleMappings();
    final String expectedId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    final List<EventProcessRoleRestDto> roles = eventProcessClient.getEventProcessMappingRoles(expectedId);

    // then
    assertThat(roles)
      .hasSize(1)
      .extracting(EventProcessRoleRestDto::getIdentity)
      .extracting(IdentityDto::getId)
      .containsExactly(DEFAULT_USERNAME);
  }

  @Test
  public void getRolesContainsUserMetadata_retrieveFromCache() {
    // given
    final UserDto expectedUserDtoWithData = new UserDto(
      DEFAULT_USERNAME, DEFAULT_FIRSTNAME, DEFAULT_LASTNAME, "me@camunda.com"
    );
    embeddedOptimizeExtension.getIdentityService().addIdentity(expectedUserDtoWithData);

    final EventProcessMappingDto eventProcessMappingDto = createEventProcessMappingDtoWithSimpleMappings();
    final String expectedId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    final List<EventProcessRoleRestDto> roles = eventProcessClient.getEventProcessMappingRoles(expectedId);

    // then
    MatcherAssert.assertThat(roles.size(), is(1));
    final IdentityWithMetadataDto identityRestDto = roles.get(0).getIdentity();
    MatcherAssert.assertThat(identityRestDto, is(instanceOf(UserDto.class)));
    final UserDto userDto = (UserDto) identityRestDto;
    MatcherAssert.assertThat(userDto.getFirstName(), is(expectedUserDtoWithData.getFirstName()));
    MatcherAssert.assertThat(userDto.getLastName(), is(expectedUserDtoWithData.getLastName()));
    MatcherAssert.assertThat(
      userDto.getName(),
      is(expectedUserDtoWithData.getFirstName() + " " + expectedUserDtoWithData.getLastName())
    );
    MatcherAssert.assertThat(userDto.getEmail(), is(expectedUserDtoWithData.getEmail()));
  }

  @Test
  public void updateEventBasedProcessRoles_singleEntry() {
    // given
    final EventProcessMappingDto eventProcessMappingDto = createEventProcessMappingDtoWithSimpleMappings();
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    engineIntegrationExtension.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_KERMIT);

    // when
    eventProcessClient.updateEventProcessMappingRoles(
      eventProcessMappingId,
      Collections.singletonList(new EventProcessRoleDto<>(new UserDto(USER_KERMIT)))
    );

    // then
    final List<EventProcessRoleRestDto> roles = eventProcessClient.getEventProcessMappingRoles(eventProcessMappingId);
    assertThat(roles)
      .hasSize(1)
      .extracting(EventProcessRoleRestDto::getIdentity)
      .extracting(IdentityDto::getId)
      .containsExactly(USER_KERMIT);
  }

  @Test
  public void updateEventBasedProcessRoles_multipleEntries() {
    // given
    final EventProcessMappingDto eventProcessMappingDto = createEventProcessMappingDtoWithSimpleMappings();
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    engineIntegrationExtension.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_KERMIT);
    engineIntegrationExtension.createGroup(TEST_GROUP);
    engineIntegrationExtension.grantGroupOptimizeAccess(TEST_GROUP);

    final ImmutableList<EventProcessRoleDto<IdentityDto>> roleEntries = ImmutableList.of(
      new EventProcessRoleDto<>(new UserDto(USER_KERMIT)),
      new EventProcessRoleDto<>(new GroupDto(TEST_GROUP))
    );
    // when
    eventProcessClient.updateEventProcessMappingRoles(eventProcessMappingId, roleEntries);

    // then
    final List<EventProcessRoleRestDto> roles = eventProcessClient.getEventProcessMappingRoles(eventProcessMappingId);
    assertThat(roles)
      .extracting(EventProcessRoleRestDto::getIdentity)
      .extracting(IdentityDto::getId, IdentityDto::getType)
      .containsExactly(
        Tuple.tuple(USER_KERMIT, IdentityType.USER),
        Tuple.tuple(TEST_GROUP, IdentityType.GROUP)
      );
  }

  @Test
  public void updateEventBasedProcessRoles_multipleEntriesMissingTypeResolved() {
    // given
    final EventProcessMappingDto eventProcessMappingDto = createEventProcessMappingDtoWithSimpleMappings();
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    engineIntegrationExtension.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_KERMIT);
    engineIntegrationExtension.createGroup(TEST_GROUP);
    engineIntegrationExtension.grantGroupOptimizeAccess(TEST_GROUP);

    final ImmutableList<EventProcessRoleDto<IdentityDto>> roleEntries = ImmutableList.of(
      new EventProcessRoleDto<>(new IdentityDto(USER_KERMIT, null)),
      new EventProcessRoleDto<>(new IdentityDto(TEST_GROUP, null))
    );
    // when
    eventProcessClient.updateEventProcessMappingRoles(eventProcessMappingId, roleEntries);

    // then
    final List<EventProcessRoleRestDto> roles = eventProcessClient.getEventProcessMappingRoles(eventProcessMappingId);
    assertThat(roles)
      .extracting(EventProcessRoleRestDto::getIdentity)
      .extracting(IdentityDto::getId, IdentityDto::getType)
      .containsExactly(
        Tuple.tuple(USER_KERMIT, IdentityType.USER),
        Tuple.tuple(TEST_GROUP, IdentityType.GROUP)
      );
  }

  @Test
  public void updateEventBasedProcessRoles_emptyListFails() {
    // given
    final EventProcessMappingDto eventProcessMappingDto = createEventProcessMappingDtoWithSimpleMappings();
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    final ErrorResponseDto updateResponse = eventProcessClient
      .createUpdateEventProcessMappingRolesRequest(eventProcessMappingId, Collections.emptyList())
      .execute(ErrorResponseDto.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then
    assertThat(updateResponse.getErrorCode()).isEqualTo(OptimizeValidationException.ERROR_CODE);
  }

  @Test
  public void updateEventBasedProcessRoles_onInvalidIdentityFail() {
    // given
    final EventProcessMappingDto eventProcessMappingDto = createEventProcessMappingDtoWithSimpleMappings();
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    final ErrorResponseDto updateResponse = eventProcessClient
      .createUpdateEventProcessMappingRolesRequest(
        eventProcessMappingId,
        Collections.singletonList(new EventProcessRoleDto<>(new UserDto("invalid")))
      )
      .execute(ErrorResponseDto.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then
    assertThat(updateResponse.getErrorCode()).isEqualTo(OptimizeValidationException.ERROR_CODE);
  }

  @Test
  public void updateEventBasedProcessRoles_onInvalidIdentityAmongValidOnesFail() {
    // given
    final EventProcessMappingDto eventProcessMappingDto = createEventProcessMappingDtoWithSimpleMappings();
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    final ErrorResponseDto updateResponse = eventProcessClient
      .createUpdateEventProcessMappingRolesRequest(
        eventProcessMappingId,
        ImmutableList.of(
          new EventProcessRoleDto<>(new UserDto("invalid")),
          new EventProcessRoleDto<>(new UserDto(DEFAULT_USERNAME))
        )
      )
      .execute(ErrorResponseDto.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then
    assertThat(updateResponse.getErrorCode()).isEqualTo(OptimizeValidationException.ERROR_CODE);
  }

  private EventProcessMappingDto createEventProcessMappingDtoWithSimpleMappings() {
    return eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
      Collections.emptyMap(),
      "process name",
      createSimpleProcessDefinitionXml()
    );
  }

}
