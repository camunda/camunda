/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions} from '@tanstack/react-query';
import {endpoints} from '#/shared/http/endpoints';
import {operateClientConfigSchema} from '#/shared/http/operateClientConfig';
import {request} from '#/shared/http/request';

const CONFIG_PREFIX = /^\s*window\.clientConfig\s*=\s*/;

function operateClientConfigQuery() {
	return queryOptions({
		queryKey: ['operateClientConfig'] as const,
		staleTime: Infinity,
		queryFn: async () => {
			const {response, error} = await request(endpoints.getOperateClientConfig());
			if (error !== null) {
				return operateClientConfigSchema.parse({});
			}
			try {
				const script = await response.text();
				return operateClientConfigSchema.parse(JSON.parse(script.replace(CONFIG_PREFIX, '').trim().replace(/;$/, '')));
			} catch {
				return operateClientConfigSchema.parse({});
			}
		},
	});
}

export {operateClientConfigQuery};
