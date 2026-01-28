/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryProcessDefinitionsRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {
  parseProcessInstancesFilter,
  type ProcessInstancesFilter,
} from './processInstancesSearch';

type ProcessDefinitionsSearchFilter = NonNullable<
  QueryProcessDefinitionsRequestBody['filter']
>;

/**
 * Parses filter arguments for a process-definitions search from the given {@linkcode URLSearchParams}.
 */
function parseProcessDefinitionsSearchFilter(
  search: URLSearchParams,
): ProcessDefinitionsSearchFilter {
  const filter = parseProcessInstancesFilter(search);

  return {
    processDefinitionId: filter.process,
    tenantId: filter.tenant === 'all' ? undefined : filter.tenant,
    version: mapProcessDefinitionVersion(filter),
  };
}

function mapProcessDefinitionVersion(
  filter: ProcessInstancesFilter,
): ProcessDefinitionsSearchFilter['version'] {
  if (filter.version && filter.version !== 'all') {
    const version = parseInt(filter.version, 10);
    if (!isNaN(version)) {
      return version;
    }
  }
}

export {parseProcessDefinitionsSearchFilter};
