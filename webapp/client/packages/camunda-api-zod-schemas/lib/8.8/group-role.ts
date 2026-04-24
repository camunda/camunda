/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * This file exists only to avoid circular dependencies. Do not export it directly.
 */

import {z} from 'zod';

const groupSchema = z.object({
	groupId: z.string(),
	name: z.string(),
	description: z.string(),
});
type Group = z.infer<typeof groupSchema>;

const roleSchema = z.object({
	roleId: z.string(),
	name: z.string(),
	description: z.string(),
});
type Role = z.infer<typeof roleSchema>;

export {groupSchema, roleSchema};
export type {Group, Role};
