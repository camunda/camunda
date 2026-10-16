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
import com.unboundid.scim2.common.types.GroupResource;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.gateway.scim.ScimJson;
import io.camunda.gateway.scim.ScimSources;
import io.camunda.gateway.scim.ScimSources.Source;
import io.camunda.gateway.scim.mapper.ScimGroupFilter;
import io.camunda.gateway.scim.mapper.ScimGroupIdGenerator;
import io.camunda.gateway.scim.mapper.ScimGroupMapper;
import io.camunda.gateway.scim.mapper.ScimGroupPatchTranslator;
import io.camunda.gateway.scim.mapper.ScimGroupPatchTranslator.MemberChange;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupAttribute;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Predicate;
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
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.node.ObjectNode;

/**
 * SCIM 2.0 {@code /Groups} endpoints of the default physical tenant. Writes go through the engine;
 * their responses are built from the record the engine returns and the members that were applied,
 * never from secondary storage. Reads return the members from secondary storage.
 *
 * <p>Supported query parameters: {@code filter} ({@code displayName eq}), {@code startIndex},
 * {@code count} and {@code excludedAttributes=members}. The {@code attributes} parameter is
 * accepted and ignored: the full resource is always returned, which keeps the {@code id}, {@code
 * schemas} and {@code meta} attributes the RFC requires in every response anyway.
 *
 * <p>Limitation: creating a group with members is two engine commands (create, then set members).
 * If the second fails, the group stays created without members and the error is reported; there is
 * no rollback, and a retry of the POST answers 409 because the group exists.
 *
 * <p>PATCH answers {@code 204 No Content}, which RFC 7644 permits. Membership additions and
 * removals do not yield the full member list without a secondary read, and a resource without its
 * members would misrepresent the group.
 */
@RestController
@RequestMapping(ScimGroupController.GROUPS_PATH)
public class ScimGroupController {

  public static final String GROUPS_PATH = "/scim/v2/Groups";
  public static final int DEFAULT_COUNT = 100;
  public static final int MAX_COUNT = 100;

  private static final int MEMBER_PAGE_SIZE = 100;
  private static final MediaType SCIM_MEDIA_TYPE = MediaType.parseMediaType(ScimJson.MEDIA_TYPE);

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final ScimGroupMapper groupMapper;
  private final ScimGroupPatchTranslator patchTranslator;
  private final ScimGroupIdGenerator idGenerator;
  private final ScimSources sources;

  public ScimGroupController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider,
      final ScimGroupMapper groupMapper,
      final ScimGroupPatchTranslator patchTranslator,
      final ScimGroupIdGenerator idGenerator,
      final ScimSources sources) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
    this.groupMapper = groupMapper;
    this.patchTranslator = patchTranslator;
    this.idGenerator = idGenerator;
    this.sources = sources;
  }

  @PostMapping
  public CompletableFuture<ResponseEntity<String>> createGroup(@RequestBody final String body)
      throws ScimException {
    final var resource = ScimJson.read(body, GroupResource.class);
    final var members = groupMapper.memberIds(resource.getMembers());
    final var groupId = idGenerator.generate(resource.getDisplayName(), resource.getExternalId());
    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var source = sources.require(authentication);
    final var dto =
        withSource(
            groupMapper.toCreateDto(resource, groupId, ScimJson.read(body, ObjectNode.class)),
            source);
    // the futures complete on a broker thread, where the request context is no longer bound
    final var location = location(groupId);
    final var memberRef = memberRef();
    final var services = groupServices();
    return services
        .createGroup(dto, authentication)
        .exceptionallyCompose(error -> adoptExisting(error, services, dto, source, authentication))
        .thenCompose(
            record ->
                setMembers(services, groupId, members, source, authentication, !members.isEmpty())
                    .thenApply(
                        ignored ->
                            ResponseEntity.status(HttpStatus.CREATED)
                                .contentType(SCIM_MEDIA_TYPE)
                                .location(location)
                                .body(
                                    ScimJson.write(
                                        groupMapper.toJson(
                                            record, members, location, memberRef)))));
  }

  @GetMapping("/{id}")
  public ResponseEntity<String> getGroup(
      @PathVariable final String id,
      @RequestParam(name = "excludedAttributes", required = false) final String excludedAttributes)
      throws ScimException {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var source = sources.require(authentication);
    final var entity = groupServices().getGroup(id, authentication);
    if (!source.canSee(entity.source())) {
      throw notFound(id);
    }
    return ok(
        ScimJson.write(
            toResource(entity, authentication, !excludesMembers(excludedAttributes), memberRef())));
  }

  @GetMapping
  public ResponseEntity<String> searchGroups(
      @RequestParam(name = "filter", required = false) final String filter,
      @RequestParam(name = "startIndex", required = false) final Integer startIndex,
      @RequestParam(name = "count", required = false) final Integer count,
      @RequestParam(name = "excludedAttributes", required = false) final String excludedAttributes)
      throws ScimException {
    final var displayName = ScimGroupFilter.parseDisplayNameEquals(filter);
    // SCIM indexes start at 1 and values below the minimum are treated as the minimum
    final int start = startIndex == null ? 1 : Math.max(startIndex, 1);
    final int size = count == null ? DEFAULT_COUNT : Math.min(Math.max(count, 0), MAX_COUNT);
    final var query =
        GroupQuery.of(
            q ->
                q.filter(f -> displayName.map(name -> f.name(name)).orElse(f))
                    // a page size of 0 would mean "unlimited", so fetch one and drop it below
                    .page(p -> p.from(start - 1).size(Math.max(size, 1))));

    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var source = sources.require(authentication);
    final var result = groupServices().search(query, authentication);

    // the source is filtered in memory after the search, so the total is reduced by the hidden
    // entities of this page only (POC)
    final var visible =
        result.items().stream().filter(entity -> source.canSee(entity.source())).toList();
    final long total = result.total() - (result.items().size() - visible.size());
    final var withMembers = !excludesMembers(excludedAttributes);
    final var memberRef = memberRef();
    final List<ObjectNode> resources =
        visible.stream()
            .limit(size)
            .map(entity -> toResource(entity, authentication, withMembers, memberRef))
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
  public CompletableFuture<ResponseEntity<String>> replaceGroup(
      @PathVariable final String id, @RequestBody final String body) throws ScimException {
    final var resource = ScimJson.read(body, GroupResource.class);
    final var members = groupMapper.memberIds(resource.getMembers());
    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var source = sources.require(authentication);
    final var dto =
        withSource(
            groupMapper.toReplaceDto(resource, id, ScimJson.read(body, ObjectNode.class)), source);
    final var location = location(id);
    final var memberRef = memberRef();
    final var services = groupServices();
    return services
        .updateGroup(dto, withAdoption(groupMapper.replacedAttributes(), source), authentication)
        .thenCompose(
            record ->
                setMembers(services, id, members, source, authentication, true)
                    .thenApply(
                        ignored ->
                            ok(
                                ScimJson.write(
                                    groupMapper.toJson(record, members, location, memberRef)))));
  }

  @PatchMapping("/{id}")
  public CompletableFuture<ResponseEntity<Void>> patchGroup(
      @PathVariable final String id, @RequestBody final String body) throws ScimException {
    final var patch = patchTranslator.translate(ScimJson.read(body, PatchRequest.class), id);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var source = sources.require(authentication);
    final var services = groupServices();
    if (patch.changedAttributes().isEmpty() && patch.memberChanges().isEmpty()) {
      // nothing to apply, but an unknown group must still answer 404
      if (!source.canSee(services.getGroup(id, authentication).source())) {
        throw notFound(id);
      }
      return CompletableFuture.completedFuture(ResponseEntity.noContent().build());
    }
    CompletableFuture<Void> applied = CompletableFuture.completedFuture(null);
    if (!patch.changedAttributes().isEmpty()) {
      applied =
          applied.thenCompose(
              ignored ->
                  services
                      .updateGroup(
                          withSource(patch.group(), source),
                          withAdoption(patch.changedAttributes(), source),
                          authentication)
                      .thenApply(record -> null));
    }
    for (final MemberChange change : patch.memberChanges()) {
      applied =
          applied.thenCompose(
              ignored -> applyMemberChange(services, id, change, source, authentication));
    }
    return applied.thenApply(ignored -> ResponseEntity.noContent().build());
  }

  @DeleteMapping("/{id}")
  public CompletableFuture<ResponseEntity<Void>> deleteGroup(@PathVariable final String id)
      throws ScimException {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var source = sources.require(authentication);
    return groupServices()
        .deleteGroup(id, source.id(), source.adoptUnowned(), authentication)
        .thenApply(record -> ResponseEntity.noContent().build());
  }

  private CompletableFuture<Void> applyMemberChange(
      final GroupServices services,
      final String groupId,
      final MemberChange change,
      final Source source,
      final CamundaAuthentication authentication) {
    return switch (change.type()) {
      case SET -> setMembers(services, groupId, change.memberIds(), source, authentication, true);
      case ADD ->
          forEachMember(
              change,
              memberId ->
                  tolerate(
                      services.assignMember(
                          member(groupId, memberId),
                          source.id(),
                          source.adoptUnowned(),
                          authentication),
                      error -> error.getStatus() == Status.ALREADY_EXISTS));
      case REMOVE ->
          forEachMember(
              change,
              memberId ->
                  tolerate(
                      services.removeMember(
                          member(groupId, memberId),
                          source.id(),
                          source.adoptUnowned(),
                          authentication),
                      ScimGroupController::isMemberNotFound));
    };
  }

  private static CompletableFuture<Void> forEachMember(
      final MemberChange change, final Function<String, CompletableFuture<Void>> call) {
    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
    for (final String memberId : change.memberIds()) {
      chain = chain.thenCompose(ignored -> call.apply(memberId));
    }
    return chain;
  }

  private static CompletableFuture<Void> setMembers(
      final GroupServices services,
      final String groupId,
      final List<String> members,
      final Source source,
      final CamundaAuthentication authentication,
      final boolean send) {
    if (!send) {
      return CompletableFuture.completedFuture(null);
    }
    return services
        .setMembers(
            groupId, EntityType.USER, members, source.id(), source.adoptUnowned(), authentication)
        .thenApply(record -> null);
  }

  /**
   * Completes normally if the failure is accepted. The engine words a missing group and a missing
   * member differently only in its message, so a NOT_FOUND on removal is a member that is already
   * gone only if the message talks about the entity; a missing group stays a 404.
   */
  private static boolean isMemberNotFound(final ServiceException error) {
    return error.getStatus() == Status.NOT_FOUND
        && error.getMessage() != null
        && error.getMessage().contains("the entity");
  }

  private static <T> CompletableFuture<Void> tolerate(
      final CompletableFuture<T> future, final Predicate<ServiceException> accepted) {
    return future.handle(
        (record, error) -> {
          if (error == null) {
            return null;
          }
          final var cause =
              error instanceof CompletionException && error.getCause() != null
                  ? error.getCause()
                  : error;
          if (cause instanceof final ServiceException serviceException
              && accepted.test(serviceException)) {
            return null;
          }
          throw error instanceof final CompletionException completion
              ? completion
              : new CompletionException(error);
        });
  }

  /**
   * A create of an existing group that no source owns becomes an update that adopts it, if the
   * source allows adoption. Every other failure, including a group of another source, is reported
   * as is.
   */
  private CompletableFuture<GroupRecord> adoptExisting(
      final Throwable error,
      final GroupServices services,
      final GroupDTO dto,
      final Source source,
      final CamundaAuthentication authentication) {
    final var cause =
        error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    if (source.adoptUnowned()
        && cause instanceof final ServiceException serviceException
        && serviceException.getStatus() == Status.ALREADY_EXISTS
        && isUnowned(services, dto.groupId(), authentication)) {
      return services.updateGroup(
          dto, withAdoption(groupMapper.replacedAttributes(), source), authentication);
    }
    return CompletableFuture.failedFuture(cause);
  }

  private static boolean isUnowned(
      final GroupServices services, final String groupId, final CamundaAuthentication auth) {
    try {
      final var source = services.getGroup(groupId, auth).source();
      return source == null || source.isEmpty();
    } catch (final RuntimeException e) {
      return false;
    }
  }

  private static GroupDTO withSource(final GroupDTO dto, final Source source) {
    return new GroupDTO(
        dto.groupId(),
        dto.name(),
        dto.description(),
        dto.externalId(),
        dto.attributes(),
        source.id());
  }

  private static Set<GroupAttribute> withAdoption(
      final Set<GroupAttribute> attributes, final Source source) {
    if (!source.adoptUnowned()) {
      return attributes;
    }
    final Set<GroupAttribute> withSource = EnumSet.copyOf(attributes);
    withSource.add(GroupAttribute.SOURCE);
    return withSource;
  }

  private static ScimException notFound(final String id) {
    return new ScimException(404, null, "Group '" + id + "' not found");
  }

  private static GroupMemberDTO member(final String groupId, final String memberId) {
    return new GroupMemberDTO(groupId, memberId, EntityType.USER);
  }

  private ObjectNode toResource(
      final GroupEntity entity,
      final CamundaAuthentication authentication,
      final boolean withMembers,
      final Function<String, URI> memberRef) {
    final var members = withMembers ? readMembers(entity.groupId(), authentication) : null;
    return groupMapper.toJson(entity, members, location(entity.groupId()), memberRef);
  }

  private List<String> readMembers(final String groupId, final CamundaAuthentication auth) {
    final List<String> usernames = new ArrayList<>();
    long total;
    do {
      final int from = usernames.size();
      final var page =
          groupServices()
              .searchMembers(
                  GroupMemberQuery.of(
                      q ->
                          q.filter(f -> f.groupId(groupId).memberType(EntityType.USER))
                              .page(p -> p.from(from).size(MEMBER_PAGE_SIZE))),
                  auth);
      total = page.total();
      page.items().forEach(member -> usernames.add(member.id()));
      if (page.items().isEmpty()) {
        break;
      }
    } while (usernames.size() < total);
    return usernames;
  }

  private static boolean excludesMembers(final String excludedAttributes) {
    return excludedAttributes != null
        && Arrays.stream(excludedAttributes.split(","))
            .anyMatch(attribute -> "members".equals(attribute.trim().toLowerCase(Locale.ROOT)));
  }

  private GroupServices groupServices() {
    return serviceRegistry.groupServices(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
  }

  private static ResponseEntity<String> ok(final String body) {
    return ResponseEntity.ok().contentType(SCIM_MEDIA_TYPE).body(body);
  }

  private static URI location(final String id) {
    return ServletUriComponentsBuilder.fromCurrentContextPath()
        .path(GROUPS_PATH + "/{id}")
        .buildAndExpand(id)
        .encode()
        .toUri();
  }

  /** Resolves the base URL while the request is still bound, so it can be used on any thread. */
  private static Function<String, URI> memberRef() {
    final var usersBase =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path(ScimUserController.USERS_PATH)
            .build()
            .toUriString();
    return username ->
        URI.create(usersBase + "/" + UriUtils.encodePathSegment(username, StandardCharsets.UTF_8));
  }
}
