/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. Licensed under a proprietary license. See the
 * License.txt file for more information. You may not use this file except in compliance with the
 * proprietary license.
 */
package io.camunda.identity.usermanagement.repository;

import io.camunda.identity.usermanagement.model.MappedUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MappedUserRepository extends JpaRepository<MappedUser, String> {}
