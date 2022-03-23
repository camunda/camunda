/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public abstract class AbstractCachedIdentityService extends AbstractIdentityService {

  protected final UserIdentityCache syncedIdentityCache;

  protected AbstractCachedIdentityService(final ConfigurationService configurationService,
                                          final UserIdentityCache syncedIdentityCache) {
    super(configurationService);
    this.syncedIdentityCache = syncedIdentityCache;
  }

  public void addIdentity(final IdentityWithMetadataResponseDto identity) {
    syncedIdentityCache.addIdentity(identity);
  }

  public IdentitySearchResultResponseDto searchForIdentitiesAsUser(final String userId,
                                                                   final String searchString,
                                                                   final int maxResults) {
    final List<IdentityWithMetadataResponseDto> filteredIdentities = new ArrayList<>();
    IdentitySearchResultResponseDto result = syncedIdentityCache.searchIdentities(
      searchString, IdentityType.values(), maxResults
    );
    while (!result.getResult().isEmpty()
      && filteredIdentities.size() < maxResults) {
      // continue searching until either the maxResult number of hits has been found or
      // the end of the cache has been reached
      filteredIdentities.addAll(filterIdentitySearchResultByUserAuthorizations(userId, result));
      result = syncedIdentityCache.searchIdentitiesAfter(searchString, IdentityType.values(), maxResults, result);
    }
    return new IdentitySearchResultResponseDto(filteredIdentities.size(), filteredIdentities);
  }

}
