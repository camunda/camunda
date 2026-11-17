/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {brokerInfoSchema} from './gen/zod/brokerInfoSchema';
import {partitionSchema} from './gen/zod/partitionSchema';
import {topologyResponseSchema} from './gen/zod/topologyResponseSchema';
import type {BrokerInfo} from './gen/types/BrokerInfo';
import type {Partition, PartitionHealthEnumKey, PartitionRoleEnumKey} from './gen/types/Partition';
import type {TopologyResponse} from './gen/types/TopologyResponse';

const partitionRoleSchema = partitionSchema.shape.role;
type PartitionRole = PartitionRoleEnumKey;

const partitionHealthSchema = partitionSchema.shape.health;
type PartitionHealth = PartitionHealthEnumKey;

const getTopologyResponseBodySchema = topologyResponseSchema;
type GetTopologyResponseBody = TopologyResponse;

const getTopology = {
	method: 'GET',
	getUrl: () => `/${API_VERSION}/topology` as const,
} as const satisfies Endpoint;

export {
	partitionRoleSchema,
	partitionHealthSchema,
	partitionSchema,
	brokerInfoSchema,
	getTopologyResponseBodySchema,
	getTopology,
};

export type {PartitionRole, PartitionHealth, Partition, BrokerInfo, GetTopologyResponseBody};
