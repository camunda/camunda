/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {describe, expect} from 'vitest';
import {useHasRouteMatch} from './useHasRouteMatch';

type RouteTo = Parameters<ReturnType<typeof useHasRouteMatch>>[number];

function TestComponent({routes}: {routes: RouteTo[]}) {
	const hasRouteMatch = useHasRouteMatch();

	const result = hasRouteMatch(...routes);
	return <div data-testid="result">{String(result)}</div>;
}

describe('useHasRouteMatch', () => {
	it('should return true when the current route matches one of the provided routes', async () => {
		const screen = await renderWithRouter(() => <TestComponent routes={['/tasklist/$userTaskKey', '/tasklist']} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/123',
		});

		await expect.element(screen.getByTestId('result')).toHaveTextContent('true');
	});

	it('should return false when none of the provided routes match', async () => {
		const screen = await renderWithRouter(() => <TestComponent routes={['/tasklist/$userTaskKey/process']} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/123',
		});

		await expect.element(screen.getByTestId('result')).toHaveTextContent('false');
	});
});
