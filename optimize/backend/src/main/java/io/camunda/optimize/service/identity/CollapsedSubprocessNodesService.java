/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.util.BpmnModelUtil;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * This cache extracts all flow node IDs from a process XML that are considered hidden from view.
 * This refers to the flow nodes within a collapsed subprocess
 */
@Component
public class CollapsedSubprocessNodesService {

  public static final int MAX_CACHE_SIZE = 10;
  public static final int CACHE_EXPIRY_MINS = 10;
  private final Cache<String, Set<String>> collapsedSubprocessNodesCache;

  public CollapsedSubprocessNodesService() {
    collapsedSubprocessNodesCache =
        CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterAccess(CACHE_EXPIRY_MINS, TimeUnit.MINUTES)
            .build();
  }

  public Set<String> getCollapsedSubprocessNodeIdsForReport(
      final ProcessReportDataDto reportDataDto) {
    final String cacheKey = buildCacheEntryKey(reportDataDto);
    final Set<String> cachedNodes = collapsedSubprocessNodesCache.getIfPresent(cacheKey);
    if (cachedNodes != null) {
      return cachedNodes;
    } else {
      final Set<String> collapsedSubprocessNodeIds =
          BpmnModelUtil.getCollapsedSubprocessElementIds(reportDataDto.getConfiguration().getXml());
      collapsedSubprocessNodesCache.put(cacheKey, collapsedSubprocessNodeIds);
      return collapsedSubprocessNodeIds;
    }
  }

  private static String buildCacheEntryKey(final ProcessReportDataDto reportDataDto) {
    return reportDataDto.getDefinitionKey() + "_v" + reportDataDto.getDefinitionVersions();
  }
}
