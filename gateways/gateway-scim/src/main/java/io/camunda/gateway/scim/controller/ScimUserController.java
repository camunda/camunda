/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.controller;

import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.types.UserResource;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.gateway.scim.ScimJson;
import io.camunda.gateway.scim.ScimSources;
import io.camunda.gateway.scim.ScimSources.Source;
import io.camunda.gateway.scim.mapper.ScimPatchTranslator;
import io.camunda.gateway.scim.mapper.ScimUserFilter;
import io.camunda.gateway.scim.mapper.ScimUserMapper;
import io.camunda.search.query.UserQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.service.UserServices;
import io.camunda.service.UserServices.UserAttribute;
import io.camunda.service.UserServices.UserDTO;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.node.ObjectNode;

/**
 * SCIM 2.0 {@code /Users} endpoints of the default physical tenant. Bodies are handled as strings
 * and (de)serialized with the SCIM SDK's mapper. Writes go through the engine; their responses are
 * built from the record the engine returns, never from secondary storage.
 */
@RestController
@RequestMapping(ScimUserController.USERS_PATH)
public class ScimUserController {

  public static final String USERS_PATH = "/scim/v2/Users";
  public static final int DEFAULT_COUNT = 100;
  public static final int MAX_COUNT = 100;

  private static final MediaType SCIM_MEDIA_TYPE = MediaType.parseMediaType(ScimJson.MEDIA_TYPE);

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final ScimUserMapper userMapper;
  private final ScimPatchTranslator patchTranslator;
  private final ScimSources sources;

  public ScimUserController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider,
      final ScimUserMapper userMapper,
      final ScimPatchTranslator patchTranslator,
      final ScimSources sources) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
    this.userMapper = userMapper;
    this.patchTranslator = patchTranslator;
    this.sources = sources;
  }

  @PostMapping
  public CompletableFuture<ResponseEntity<String>> createUser(@RequestBody final String body)
      throws ScimException {
    final var resource = ScimJson.read(body, UserResource.class);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var source = sources.require(authentication);
    final var dto =
        withSource(userMapper.toCreateDto(resource, ScimJson.read(body, ObjectNode.class)), source);
    // the future completes on a broker thread, where the request context is no longer bound
    final var location = location(dto.username());
    return userServices()
        .createUser(dto, authentication)
        .exceptionallyCompose(error -> adoptExisting(error, dto, resource, source, authentication))
        .thenApply(
            record ->
                ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(SCIM_MEDIA_TYPE)
                    .location(location)
                    .body(ScimJson.write(userMapper.toJson(record, location))));
  }

  @GetMapping("/{id}")
  public ResponseEntity<String> getUser(@PathVariable final String id) throws ScimException {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var source = sources.require(authentication);
    final var entity = userServices().getUser(id, authentication);
    if (!source.canSee(entity.source())) {
      throw notFound(id);
    }
    return ok(ScimJson.write(userMapper.toJson(entity, location(entity.username()))));
  }

  @GetMapping
  public ResponseEntity<String> searchUsers(
      @RequestParam(name = "filter", required = false) final String filter,
      @RequestParam(name = "startIndex", required = false) final Integer startIndex,
      @RequestParam(name = "count", required = false) final Integer count)
      throws ScimException {
    final var userName = ScimUserFilter.parseUserNameEquals(filter);
    // SCIM indexes start at 1 and values below the minimum are treated as the minimum
    final int start = startIndex == null ? 1 : Math.max(startIndex, 1);
    final int size = count == null ? DEFAULT_COUNT : Math.min(Math.max(count, 0), MAX_COUNT);
    final var query =
        UserQuery.of(
            q ->
                q.filter(f -> userName.map(f::usernames).orElse(f))
                    // a page size of 0 would mean "unlimited", so fetch one and drop it below
                    .page(p -> p.from(start - 1).size(Math.max(size, 1))));

    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var source = sources.require(authentication);
    final var result = userServices().search(query, authentication);

    // the source is filtered in memory after the search, so the total is reduced by the hidden
    // entities of this page only (POC)
    final var visible =
        result.items().stream().filter(entity -> source.canSee(entity.source())).toList();
    final long total = result.total() - (result.items().size() - visible.size());
    final List<ObjectNode> resources =
        visible.stream()
            .limit(size)
            .map(entity -> userMapper.toJson(entity, location(entity.username())))
            .toList();
    return ok(
        ScimJson.write(
            new ListResponse<>(
                total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total,
                resources,
                start,
                resources.size())));
  }

  @PutMapping("/{id}")
  public CompletableFuture<ResponseEntity<String>> replaceUser(
      @PathVariable final String id, @RequestBody final String body) throws ScimException {
    final var resource = ScimJson.read(body, UserResource.class);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var source = sources.require(authentication);
    final var dto =
        withSource(
            userMapper.toReplaceDto(resource, id, ScimJson.read(body, ObjectNode.class)), source);
    final var location = location(id);
    return userServices()
        .updateUser(
            dto, withAdoption(userMapper.replacedAttributes(resource), source), authentication)
        .thenApply(record -> ok(ScimJson.write(userMapper.toJson(record, location))));
  }

  @PatchMapping("/{id}")
  public CompletableFuture<ResponseEntity<String>> patchUser(
      @PathVariable final String id, @RequestBody final String body) throws ScimException {
    final var patch = patchTranslator.translate(ScimJson.read(body, PatchRequest.class), id);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var source = sources.require(authentication);
    if (patch.changedAttributes().isEmpty()) {
      // an update without a mask would overwrite the user, so there is nothing to send
      return CompletableFuture.completedFuture(getUser(id));
    }
    final var location = location(id);
    return userServices()
        .updateUser(
            withSource(patch.user(), source),
            withAdoption(patch.changedAttributes(), source),
            authentication)
        .thenApply(record -> ok(ScimJson.write(userMapper.toJson(record, location))));
  }

  @DeleteMapping("/{id}")
  public CompletableFuture<ResponseEntity<Void>> deleteUser(@PathVariable final String id)
      throws ScimException {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var source = sources.require(authentication);
    return userServices()
        .deleteUser(id, source.id(), source.adoptUnowned(), authentication)
        .thenApply(record -> ResponseEntity.noContent().build());
  }

  /**
   * A create of an existing user that no source owns becomes an update that adopts it, if the
   * source allows adoption. Every other failure, including a user of another source, is reported as
   * is.
   */
  private CompletableFuture<UserRecord> adoptExisting(
      final Throwable error,
      final UserDTO dto,
      final UserResource resource,
      final Source source,
      final CamundaAuthentication authentication) {
    final var cause =
        error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    if (source.adoptUnowned()
        && cause instanceof final ServiceException serviceException
        && serviceException.getStatus() == Status.ALREADY_EXISTS
        && isUnowned(dto.username(), authentication)) {
      return userServices()
          .updateUser(
              dto, withAdoption(userMapper.replacedAttributes(resource), source), authentication);
    }
    return CompletableFuture.failedFuture(cause);
  }

  private boolean isUnowned(final String username, final CamundaAuthentication authentication) {
    try {
      final var source = userServices().getUser(username, authentication).source();
      return source == null || source.isEmpty();
    } catch (final RuntimeException e) {
      return false;
    }
  }

  private static UserDTO withSource(final UserDTO dto, final Source source) {
    return new UserDTO(
        dto.username(),
        dto.name(),
        dto.email(),
        dto.password(),
        dto.externalId(),
        dto.active(),
        dto.attributes(),
        source.id());
  }

  private static Set<UserAttribute> withAdoption(
      final Set<UserAttribute> attributes, final Source source) {
    if (!source.adoptUnowned()) {
      return attributes;
    }
    final Set<UserAttribute> withSource = EnumSet.copyOf(attributes);
    withSource.add(UserAttribute.SOURCE);
    return withSource;
  }

  private static ScimException notFound(final String id) {
    return new ScimException(404, null, "User '" + id + "' not found");
  }

  private UserServices userServices() {
    return serviceRegistry.userServices(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
  }

  private static ResponseEntity<String> ok(final String body) {
    return ResponseEntity.ok().contentType(SCIM_MEDIA_TYPE).body(body);
  }

  private static URI location(final String id) {
    return ServletUriComponentsBuilder.fromCurrentContextPath()
        .path(USERS_PATH + "/{id}")
        .buildAndExpand(id)
        .encode()
        .toUri();
  }
}
