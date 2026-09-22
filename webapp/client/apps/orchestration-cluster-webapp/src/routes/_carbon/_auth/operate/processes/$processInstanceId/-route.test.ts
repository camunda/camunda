/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient} from '@tanstack/react-query';
import {expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {loadProcessInstance} from './route';

it('should rethrow unexpected process instance loader errors', async () => {
	const queryClient = new QueryClient();
	const unexpectedError = new Error('Unexpected loader error');
	vi.spyOn(queryClient, 'ensureQueryData').mockRejectedValue(unexpectedError);

	await expect(loadProcessInstance({queryClient, processInstanceId: '1'})).rejects.toBe(unexpectedError);
});
