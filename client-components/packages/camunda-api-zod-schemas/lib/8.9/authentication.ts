/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {camundaUserResultSchema} from './gen';

const currentUserSchema = camundaUserResultSchema;
type CurrentUser = z.infer<typeof currentUserSchema>;

const getCurrentUser: Endpoint = {
	method: 'GET',
	getUrl: () => `/${API_VERSION}/authentication/me`,
};

export {currentUserSchema, getCurrentUser};
export type {CurrentUser};
