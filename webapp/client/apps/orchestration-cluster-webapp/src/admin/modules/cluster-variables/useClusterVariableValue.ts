/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import type {ClusterVariable} from '@camunda/camunda-api-zod-schemas/8.11';
import {queries} from '#/shared/http/queries';

// The search endpoint truncates long values, so the full value is always fetched on demand.
function useClusterVariableValue(clusterVariable: Pick<ClusterVariable, 'name' | 'scope' | 'tenantId'>) {
	return useQuery(queries.getClusterVariable(clusterVariable));
}

export {useClusterVariableValue};
