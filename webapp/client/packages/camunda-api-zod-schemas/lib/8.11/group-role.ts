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

import {groupResultSchema} from './gen/zod/groupResultSchema';
import {roleResultSchema} from './gen/zod/roleResultSchema';
import type {GroupResult} from './gen/types/GroupResult';
import type {RoleResult} from './gen/types/RoleResult';

const groupSchema = groupResultSchema;
type Group = GroupResult;

const roleSchema = roleResultSchema;
type Role = RoleResult;

export {groupSchema, roleSchema};
export type {Group, Role};
