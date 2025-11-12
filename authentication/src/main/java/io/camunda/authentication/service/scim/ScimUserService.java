/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service.scim;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.UserServices;
import io.camunda.service.UserServices.UserDTO;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.directory.scim.core.repository.Repository;
import org.apache.directory.scim.server.configuration.ServerConfiguration;
import org.apache.directory.scim.spec.exception.ResourceException;
import org.apache.directory.scim.spec.filter.Filter;
import org.apache.directory.scim.spec.filter.FilterResponse;
import org.apache.directory.scim.spec.filter.PageRequest;
import org.apache.directory.scim.spec.filter.SortRequest;
import org.apache.directory.scim.spec.filter.attribute.AttributeReference;
import org.apache.directory.scim.spec.patch.PatchOperation;
import org.apache.directory.scim.spec.resources.ScimUser;
import org.apache.directory.scim.spring.ScimpleSpringConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScimUserService implements Repository<ScimUser> {

  private static final Logger LOG = LoggerFactory.getLogger(ScimUserService.class);

  private final UserServices userServices;

  public ScimUserService(final UserServices userServices) {
    this.userServices = userServices;
    final String name = ScimpleSpringConfiguration.class.getName();
    final String name1 = ServerConfiguration.class.getName();
  }

  @Override
  public Class<ScimUser> getResourceClass() {
    return ScimUser.class;
  }

  @Override
  public ScimUser create(final ScimUser scimUser) throws ResourceException {
    LOG.info("create user {}", scimUser);
    try {

      final UserRecord userRecord =
          userServices
              .withAuthentication(CamundaAuthentication.anonymous())
              .createUser(map(scimUser))
              .get();
      scimUser.setId(userRecord.getUsername());
      return scimUser;
    } catch (final InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public ScimUser update(
      final String s,
      final String s1,
      final ScimUser scimUser,
      final Set<AttributeReference> set,
      final Set<AttributeReference> set1)
      throws ResourceException {
    LOG.info("update user {}", scimUser);
    return null;
  }

  @Override
  public ScimUser patch(
      final String s,
      final String s1,
      final List<PatchOperation> list,
      final Set<AttributeReference> set,
      final Set<AttributeReference> set1)
      throws ResourceException {
    return null;
  }

  @Override
  public ScimUser get(final String s) throws ResourceException {
    LOG.info("get user {}", s);
    return null;
  }

  @Override
  public FilterResponse<ScimUser> find(
      final Filter filter, final PageRequest pageRequest, final SortRequest sortRequest)
      throws ResourceException {
    return null;
  }

  @Override
  public void delete(final String s) throws ResourceException {}

  private UserDTO map(final ScimUser user) {
    return new UserDTO(user.getUserName(), user.getDisplayName(), null, "password");
  }
}
