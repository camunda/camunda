/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import static io.camunda.optimize.dto.optimize.IdentityType.GROUP;
import static io.camunda.optimize.dto.optimize.IdentityType.USER;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.optimize.dto.optimize.GroupDto;
import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import io.camunda.optimize.service.util.configuration.CacheConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class UserTaskIdentityService {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(UserTaskIdentityService.class);
  private final AbstractIdentityService identityService;
  // These caches hold the user/group IDs that we cannot find in the "real" identityCache and
  // identity provider. Since users can set any string as assignee/candidate group, these are
  // assumed to be external IDs, and we use these caches to minimise how often we unnecessarily
  // query the real identity services for them.
  // We have separate assignee and candidate group caches because we don't know if IDs are unique
  // between users and groups if they're external.
  private final Cache<String, UserDto> externalUserTaskAssignees;
  private final Cache<String, GroupDto> externalUserTaskCandidateGroups;

  public UserTaskIdentityService(
      final AbstractIdentityService identityService,
      final ConfigurationService configurationService) {
    this.identityService = identityService;
    final CacheConfiguration usersCacheConfig = configurationService.getCaches().getUsers();
    externalUserTaskAssignees =
        Caffeine.newBuilder()
            .maximumSize(usersCacheConfig.getMaxSize())
            .expireAfterWrite(Duration.ofMillis(usersCacheConfig.getDefaultTtlMillis()))
            .build();
    externalUserTaskCandidateGroups =
        Caffeine.newBuilder()
            .maximumSize(usersCacheConfig.getMaxSize())
            .expireAfterWrite(Duration.ofMillis(usersCacheConfig.getDefaultTtlMillis()))
            .build();
  }

  public List<GroupDto> getCandidateGroupIdentitiesById(final Collection<String> ids) {
    return getIdentities(
            ids.stream()
                .map(id -> new IdentityDto(id, IdentityType.GROUP))
                .collect(Collectors.toSet()))
        .stream()
        .filter(GroupDto.class::isInstance)
        .map(GroupDto.class::cast)
        .toList();
  }

  public List<UserDto> getAssigneesByIds(final Collection<String> assigneeIds) {
    return getIdentities(
            assigneeIds.stream()
                .map(id -> new IdentityDto(id, IdentityType.USER))
                .collect(Collectors.toSet()))
        .stream()
        .filter(UserDto.class::isInstance)
        .map(UserDto.class::cast)
        .toList();
  }

  public IdentitySearchResultResponseDto searchAmongIdentitiesWithIds(
      final String terms,
      final Collection<String> identityIds,
      final IdentityType identityType,
      final int resultLimit) {
    if (StringUtils.isEmpty(terms)) {
      LOG.debug("Searching with empty search term. Retrieving all identities for given IDs.");
      return new IdentitySearchResultResponseDto(
          getIdentitiesByIdOrReturnDefaultDto(
              new HashSet<>(identityIds), identityType, resultLimit));
    } else {
      return searchAmongIdentitiesWithIdsOrReturnDefaultDto(
          terms, identityIds, identityType, resultLimit);
    }
  }

  public List<IdentityWithMetadataResponseDto> getIdentities(
      final Collection<IdentityDto> identities) {
    return identities.stream()
        .collect(groupingBy(IdentityDto::getType, mapping(IdentityDto::getId, Collectors.toSet())))
        .entrySet()
        .stream()
        .flatMap(
            entry -> getIdentitiesByIdOrReturnDefaultDto(entry.getValue(), entry.getKey()).stream())
        .toList();
  }

  public Optional<IdentityWithMetadataResponseDto> getIdentityByIdAndType(
      final String id, final IdentityType type) {
    return USER == type
        ? Optional.of(getUserByIdAndAddToCacheIfNotFound(id))
        : Optional.of(getGroupByIdAndAddToCacheIfNotFound(id));
  }

  private List<IdentityWithMetadataResponseDto> getIdentitiesByIdOrReturnDefaultDto(
      final Set<String> ids, final IdentityType type) {
    return getIdentitiesByIdOrReturnDefaultDto(ids, type, ids.size());
  }

  private List<IdentityWithMetadataResponseDto> getIdentitiesByIdOrReturnDefaultDto(
      final Set<String> ids, final IdentityType type, final int resultLimit) {
    return USER == type
        ? getUsersByIdAndAddToCacheIfNotFound(ids, resultLimit).stream()
            .map(IdentityWithMetadataResponseDto.class::cast)
            .collect(toList())
        : getGroupsByIdAndAddToCacheIfNotFound(ids, resultLimit).stream()
            .map(IdentityWithMetadataResponseDto.class::cast)
            .collect(toList());
  }

  private IdentityWithMetadataResponseDto getUserByIdAndAddToCacheIfNotFound(final String id) {
    // First check if this ID is already known as an external assignee ID. Then if not, check the
    // real identity cache if it is a real user. If both caches don't contain the ID, add to the
    // external user cache
    return Optional.ofNullable(externalUserTaskAssignees.getIfPresent(id))
        .orElseGet(
            () -> {
              LOG.debug(
                  "No user found in external user cache for ID [{}]. Looking up in identityService instead.",
                  id);
              return identityService
                  .getUserById(id)
                  .orElseGet(
                      () -> {
                        LOG.debug(
                            "No user found in identityService for ID [{}]. Adding to external user cache.",
                            id);
                        externalUserTaskAssignees.put(id, new UserDto(id));
                        return new UserDto(id);
                      });
            });
  }

  private List<IdentityWithMetadataResponseDto> getUsersByIdAndAddToCacheIfNotFound(
      Set<String> ids, final int resultLimit) {
    ids = ids.stream().sorted().limit(resultLimit).collect(toSet());
    LOG.debug("Attempting to retrieve users from external user cache for IDs [{}].", ids);
    // first check if any IDs are already known as external users
    final Map<String, UserDto> externalUsers = externalUserTaskAssignees.getAllPresent(ids);
    final List<IdentityWithMetadataResponseDto> result = new ArrayList<>(externalUsers.values());
    // then attempt to retrieve the rest from the real user cache
    ids.removeAll(externalUsers.keySet());
    if (!ids.isEmpty() && externalUsers.size() < resultLimit) {
      LOG.debug(
          "No users found in external user cache for IDs [{}]. Attempting to retrieve from identityService instead.",
          ids);
      final List<IdentityWithMetadataResponseDto> existingUsers = identityService.getUsersById(ids);
      result.addAll(existingUsers);
      // if there are still unknown IDs left, add them to the external cache
      ids.removeAll(
          existingUsers.stream().map(IdentityWithMetadataResponseDto::getId).collect(toSet()));
      ids.forEach(id -> result.add(externalUserTaskAssignees.get(id, UserDto::new)));
    }
    return result.stream().limit(resultLimit).toList();
  }

  private IdentityWithMetadataResponseDto getGroupByIdAndAddToCacheIfNotFound(final String id) {
    // First check if this ID is already known as an external candidate group ID. Then if not, check
    // the  real identity cache. If both caches don't contain the ID, add to the external candidate
    // group cache.
    return Optional.ofNullable(externalUserTaskCandidateGroups.getIfPresent(id))
        .orElseGet(
            () -> {
              LOG.debug(
                  "No group found in external group cache for ID [{}]. Looking up in identityService instead.",
                  id);
              return identityService
                  .getGroupById(id)
                  .orElseGet(
                      () -> {
                        LOG.debug(
                            "No group found in identityService for ID [{}]. Adding to external group cache.",
                            id);
                        externalUserTaskCandidateGroups.put(id, new GroupDto(id));
                        return new GroupDto(id);
                      });
            });
  }

  private List<IdentityWithMetadataResponseDto> getGroupsByIdAndAddToCacheIfNotFound(
      Set<String> ids, final int resultLimit) {
    ids = ids.stream().sorted().limit(resultLimit).collect(toSet());
    LOG.debug("Attempting to retrieve groups from external group cache for IDs [{}].", ids);
    // first check if any IDs are already known as external groups
    final Map<String, GroupDto> externalGroups = externalUserTaskCandidateGroups.getAllPresent(ids);
    final List<IdentityWithMetadataResponseDto> result = new ArrayList<>(externalGroups.values());
    // then attempt to retrieve the rest from the real group cache
    ids.removeAll(externalGroups.keySet());
    if (!ids.isEmpty() && result.size() < resultLimit) {
      LOG.debug(
          "No groups found in external group cache for IDs [{}]. Attempting to retrieve from identityService instead.",
          ids);
      final List<IdentityWithMetadataResponseDto> existingGroups =
          identityService.getGroupsById(ids);
      result.addAll(existingGroups);
      // if there are still unknown IDs left, add them to the external cache
      ids.removeAll(
          existingGroups.stream().map(IdentityWithMetadataResponseDto::getId).collect(toSet()));
      ids.forEach(id -> result.add(externalUserTaskCandidateGroups.get(id, GroupDto::new)));
    }
    return result.stream().limit(resultLimit).toList();
  }

  private IdentitySearchResultResponseDto searchAmongIdentitiesWithIdsOrReturnDefaultDto(
      final String terms,
      final Collection<String> identityIds,
      final IdentityType identityType,
      final int resultLimit) {
    if (GROUP == identityType) {
      // Group search is not yet available in cloud, so only check/add to the external group cache
      LOG.debug(
          "Searching for groups in external group cache for IDs [{}] and searchterm [{}].",
          identityIds,
          terms);
      return new IdentitySearchResultResponseDto(
          externalUserTaskCandidateGroups
              .getAll(
                  identityIds,
                  ids ->
                      ids.stream()
                          .map(GroupDto::new)
                          .collect(toMap(GroupDto::getId, Function.identity())))
              .values()
              .stream()
              .filter(group -> group.isIdentityContainsSearchTerm(terms))
              .map(IdentityWithMetadataResponseDto.class::cast)
              .limit(resultLimit)
              .toList());
    } else {
      LOG.debug(
          "Searching for users in external user cache for IDs [{}] and searchterm [{}].",
          identityIds,
          terms);
      final Map<String, UserDto> externalUsers =
          externalUserTaskAssignees.getAllPresent(identityIds);
      identityIds.removeAll(externalUsers.keySet());
      final List<IdentityWithMetadataResponseDto> result =
          externalUsers.values().stream()
              .filter(user -> user.isIdentityContainsSearchTerm(terms))
              .map(IdentityWithMetadataResponseDto.class::cast)
              .limit(resultLimit)
              .collect(toList());
      if (!identityIds.isEmpty() && result.size() < resultLimit) {
        LOG.debug(
            "Searching for users in identityService for IDs [{}] and searchterm [{}].",
            identityIds,
            terms);
        result.addAll(
            identityService
                .searchForIdentitiesAsUser(null, terms, resultLimit, true)
                .getResult()
                .stream()
                .filter(user -> identityIds.contains(user.toIdentityDto().getId()))
                .toList());
      }
      return new IdentitySearchResultResponseDto(result.stream().limit(resultLimit).toList());
    }
  }
}
