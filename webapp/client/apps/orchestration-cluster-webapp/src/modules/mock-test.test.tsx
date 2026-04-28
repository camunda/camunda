/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/testing-modules/test-extend';
import {Button} from '@carbon/react';
import {useEffect, useState} from 'react';
import {render} from 'vitest-browser-react';
import {http, HttpResponse} from 'msw';

function MockComponent() {
	const [response, setResponse] = useState<'network-error' | 'response-error' | 'success' | null>(null);

	useEffect(() => {
		async function doFetch() {
			try {
				const response = await fetch('/api/data', {
					method: 'POST',
					body: JSON.stringify({
						key: 'value',
					}),
				});

				if (response.ok) {
					setResponse('success');
				} else {
					setResponse('response-error');
				}
				return;
			} catch {
				setResponse('network-error');
				return;
			}
		}

		doFetch();
	}, []);

	return (
		<div>
			<p>Response: {JSON.stringify(response)}</p>
			<Button kind="danger">Click me</Button>
		</div>
	);
}

describe('foobar', () => {
	it('shoud make request', async ({worker}) => {
		worker.use(
			http.post('/api/data', () => {
				return HttpResponse.json({data: 'value'});
			}),
		);
		const screen = await render(<MockComponent />);

		await expect.element(screen.getByRole('button', {name: 'Click me'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Click me'})).toHaveTextContent('Click me');

		await expect.element(screen.getByText(/Response:/)).toHaveTextContent('Response: "success"');
	});

	it('shoud handle response error', async ({worker}) => {
		worker.use(
			http.post('/api/data', () => {
				return HttpResponse.json({error: 'bad request'}, {status: 400});
			}),
		);
		const screen = await render(<MockComponent />);

		await expect.element(screen.getByText(/Response:/)).toHaveTextContent('Response: "response-error"');
	});

	it('shoud handle network error', async ({worker}) => {
		worker.use(
			http.post('/api/data', () => {
				return HttpResponse.error();
			}),
		);
		const screen = await render(<MockComponent />);

		await expect.element(screen.getByText(/Response:/)).toHaveTextContent('Response: "network-error"');
	});
});
