/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {partitionSchema, brokerInfoSchema, topologyResponseSchema} from './gen';

const partitionRoleSchema = z.enum(['leader', 'follower', 'inactive']);
type PartitionRole = z.infer<typeof partitionRoleSchema>;

const partitionHealthSchema = z.enum(['healthy', 'unhealthy', 'dead']);
type PartitionHealth = z.infer<typeof partitionHealthSchema>;

const clusterPartitionSchema = partitionSchema;
type Partition = z.infer<typeof clusterPartitionSchema>;

const clusterBrokerInfoSchema = brokerInfoSchema;
type BrokerInfo = z.infer<typeof clusterBrokerInfoSchema>;

const getTopologyResponseBodySchema = topologyResponseSchema;
type GetTopologyResponseBody = z.infer<typeof getTopologyResponseBodySchema>;

const getTopology: Endpoint = {
	method: 'GET',
	getUrl: () => `/${API_VERSION}/topology`,
};

export {
	partitionRoleSchema,
	partitionHealthSchema,
	clusterPartitionSchema as partitionSchema,
	clusterBrokerInfoSchema as brokerInfoSchema,
	getTopologyResponseBodySchema,
	getTopology,
};

export type {PartitionRole, PartitionHealth, Partition, BrokerInfo, GetTopologyResponseBody};
