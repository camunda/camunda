/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';

const partitionRoleSchema = z.enum(['leader', 'follower', 'inactive']);
type PartitionRole = z.infer<typeof partitionRoleSchema>;

const partitionHealthSchema = z.enum(['healthy', 'unhealthy', 'dead']);
type PartitionHealth = z.infer<typeof partitionHealthSchema>;

const partitionSchema = z.object({
	partitionId: z.number().int(),
	role: partitionRoleSchema,
	health: partitionHealthSchema,
});
type Partition = z.infer<typeof partitionSchema>;

const brokerInfoSchema = z.object({
	nodeId: z.number().int(),
	host: z.string(),
	port: z.number().int(),
	partitions: z.array(partitionSchema),
	version: z.string(),
});
type BrokerInfo = z.infer<typeof brokerInfoSchema>;

const getTopologyResponseBodySchema = z.object({
	brokers: z.array(brokerInfoSchema).nullable(),
	clusterSize: z.number().int().nullable(),
	partitionsCount: z.number().int().nullable(),
	replicationFactor: z.number().int().nullable(),
	gatewayVersion: z.string().nullable(),
	lastCompletedChangeId: z.string().nullable(),
});
type GetTopologyResponseBody = z.infer<typeof getTopologyResponseBodySchema>;

const getTopology: Endpoint = {
	method: 'GET',
	getUrl: () => `/${API_VERSION}/topology`,
};

export {
	partitionRoleSchema,
	partitionHealthSchema,
	partitionSchema,
	brokerInfoSchema,
	getTopologyResponseBodySchema,
	getTopology,
};

export type {PartitionRole, PartitionHealth, Partition, BrokerInfo, GetTopologyResponseBody};
