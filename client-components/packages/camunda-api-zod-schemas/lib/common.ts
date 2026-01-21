/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const API_VERSION = 'v2';

interface Endpoint<URLParams extends object | undefined = undefined> {
	getUrl: URLParams extends undefined
		? () => string
		: {} extends URLParams
			? (params?: URLParams) => string
			: (params: URLParams) => string;
	method: string;
}

export {API_VERSION};
export type {Endpoint};
