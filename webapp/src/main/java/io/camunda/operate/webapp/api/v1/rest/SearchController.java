/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.rest;

import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

public interface SearchController<T> {

  String SEARCH = "/search";
  String BY_KEY = "/{key}";

  @ResponseStatus(HttpStatus.OK)
  @PostMapping( value = SEARCH,
      consumes = {MediaType.APPLICATION_JSON_VALUE},
      produces = {MediaType.APPLICATION_JSON_VALUE}
  )
  Results<T> search(Query<T> query);

  @ResponseStatus(HttpStatus.OK)
  @GetMapping(value = BY_KEY,
      produces = {MediaType.APPLICATION_JSON_VALUE}
  )
  T byKey(@Valid @PathVariable final Long key);

}
