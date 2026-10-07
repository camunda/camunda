/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, onTestFinished, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {useEffect, useState} from 'react';
import {useRouterState} from '@tanstack/react-router';
import {HttpResponse, http} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {
	mockGetElementInstanceEndpoint,
	mockQueryElementInstancesEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {ProcessInstancePageProvider} from './ProcessInstancePageProvider';
import {useInstanceHistory} from './useInstanceHistory';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {processInstanceSearchSchema} from './processInstanceSearch';
import {InstanceHistory} from './InstanceHistory';
import {useProcessInstancePage} from './useProcessInstancePage';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {endpoints} from '#/shared/http/endpoints';
const instance = createProcessInstance();
const child = createElementInstance();
const path = '/operate/processes/$processInstanceId/variables';
function renderPage(search = '') {
	return renderWithRouter(Harness, {
		path,
		initialEntry: `/operate/processes/${instance.processInstanceKey}/variables?${search}`,
	});
}
function Probe() {
	const history = useInstanceHistory();
	const {setVisible} = history;
	const selection = useProcessInstanceElementSelection();
	const {processInstanceId} = useProcessInstancePage();
	const window = history.windows.get(processInstanceId);
	useEffect(() => {
		setVisible(true);
		return () => setVisible(false);
	}, [setVisible]);
	return (
		<>
			<output>
				{JSON.stringify({
					key: selection.resolvedElementInstance?.elementInstanceKey ?? null,
					count: selection.selectedElementInstanceCount,
					error: selection.isFetchingElementError,
					fetching: selection.isFetchingElement,
					selected: selection.hasSelection,
					from: window?.scope.from,
					first: window?.query.data?.items[0]?.elementInstanceKey,
					forbidden: history.forbidden,
					timestamps: history.timestamps,
					nestedReady: history.windows.get(child.elementInstanceKey)?.query.isSuccess ?? false,
					nestedError: Boolean(history.windows.get(child.elementInstanceKey)?.error),
					ready: window?.query.isSuccess,
					historyError: window?.query.isError,
				})}
			</output>
			<button onClick={() => void history.page(processInstanceId, 'next')}>Next</button>
			<button onClick={() => void history.page(processInstanceId, 'previous')}>Previous</button>
			<button onClick={() => history.toggle(processInstanceId)}>Fold</button>
			<button onClick={() => history.setTimestamps(true)}>Timestamps</button>
			<button onClick={() => history.toggle(child.elementInstanceKey, instance.processInstanceKey)}>
				Expand nested
			</button>
			<button onClick={() => void history.page(child.elementInstanceKey, 'next')}>Next nested</button>
			<button onClick={history.retry}>Retry</button>
			<button
				onClick={() =>
					void selection.selectElementInstance(
						createElementInstance({...child, type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE'}),
					)
				}
			>
				Select ad-hoc instance
			</button>
		</>
	);
}
function Harness({tree = false}: {tree?: boolean}) {
	const search = useRouterState({select: (state) => state.location.search});
	const [id, setId] = useState(instance.processInstanceKey);
	const [shown, setShown] = useState(true);
	return (
		<>
			<button onClick={() => setShown(!shown)}>Remount</button>
			<button onClick={() => setId(id === instance.processInstanceKey ? 'other' : instance.processInstanceKey)}>
				Instance
			</button>
			<ProcessInstancePageProvider
				processInstanceId={id}
				processInstance={createProcessInstance({processInstanceKey: id})}
				search={processInstanceSearchSchema.parse(search)}
			>
				{shown && (tree ? <InstanceHistory /> : <Probe />)}
			</ProcessInstancePageProvider>
		</>
	);
}
describe('InstanceHistory', () => {
	afterEach(async () => {
		await cleanup();
		vi.useRealTimers();
		vi.restoreAllMocks();
		notificationsStore.reset();
	});
	it.for([0, 1, 3, 'key', 'placeholder', 'key-error', 'id-error', 'capped-one'])(
		'should resolve selection without broadening process scope (%s)',
		async (mode, {worker}) => {
			const failed = String(mode).endsWith('error');
			const keyed = ['key', 'placeholder', 'key-error'].includes(String(mode));
			const placeholder = mode === 'placeholder';
			const total = typeof mode === 'number' ? mode : 1;
			const response = createQueryElementInstancesResponse([child], total);
			response.page.hasMoreTotalItems = mode === 'capped-one';
			const get = vi.fn(() => (failed ? new HttpResponse(null, {status: 500}) : HttpResponse.json(child)));
			worker.use(
				http.get('/v2/element-instances/:key', get),
				mockQueryElementInstancesEndpoint({
					successResponse: failed ? new HttpResponse(null, {status: 500}) : HttpResponse.json(response),
				}),
			);
			const screen = await renderPage(
				`elementId=user-task${keyed ? `&elementInstanceKey=${child.elementInstanceKey}` : ''}${placeholder ? '&isPlaceholder=true' : ''}`,
			);
			if (keyed) {
				screen.queryClient.setQueryData(
					['selectedElementInstances', instance.processInstanceKey, 'user-task', false],
					createQueryElementInstancesResponse([createElementInstance({elementInstanceKey: 'cached'})]),
				);
			}
			const count = failed || placeholder ? null : total;
			const resolved =
				!failed && !placeholder && mode !== 'capped-one' && (keyed || total === 1)
					? `"${child.elementInstanceKey}"`
					: 'null';
			await expect
				.element(screen.getByText(new RegExp(`"count":${count},"error":${failed},"fetching":false,"selected":true`)))
				.toBeVisible();
			await expect.element(screen.getByText(new RegExp(`"key":${resolved}`))).toBeVisible();
			expect(get).toHaveBeenCalledTimes(keyed && !placeholder ? 1 : 0);
		},
	);
	it.for([
		{total: 53, capped: false},
		{total: 153, capped: false},
		{total: 153, capped: true},
	])(
		'should use replacement windows with 50-offset stride, total=$total capped=$capped',
		async ({total, capped}, {worker}) => {
			const offsets: number[] = [];
			worker.use(
				http.post('/v2/element-instances/search', async ({request}) => {
					const body = (await request.json()) as {page: {from: number; limit: number}};
					offsets.push(body.page.from);
					expect(body.page.limit).toBe(100);
					const response = createQueryElementInstancesResponse(
						Array.from({length: Math.min(100, total - body.page.from)}, (_, index) =>
							createElementInstance({elementInstanceKey: String(index + body.page.from)}),
						),
						capped ? 50 : total,
					);
					response.page.hasMoreTotalItems = capped;
					return HttpResponse.json(response);
				}),
			);
			const screen = await renderPage();
			await expect.element(screen.getByText(/"first":"0"/)).toBeVisible();
			await userEvent.click(screen.getByRole('button', {name: 'Next'}));
			if (total === 153) {
				await expect.element(screen.getByText(/"from":50,"first":"50"/)).toBeVisible();
				await userEvent.click(screen.getByRole('button', {name: 'Next'}));
				await expect.element(screen.getByText(/"from":100,"first":"100"/)).toBeVisible();
				await userEvent.click(screen.getByRole('button', {name: 'Next'}));
				await userEvent.click(screen.getByRole('button', {name: 'Previous'}));
				await expect.element(screen.getByText(/"from":50,"first":"50"/)).toBeVisible();
				await userEvent.click(screen.getByRole('button', {name: 'Previous'}));
				await expect.element(screen.getByText(/"from":0,"first":"0"/)).toBeVisible();
			}
			expect(offsets).toEqual(total === 53 ? [0] : [0, 50, 100, 50, 0]);
		},
	);
	it.for([403, 500])(
		'should fail closed only for forbidden ad-hoc child-scope reads (%i)',
		async (status, {worker}) => {
			vi.useFakeTimers({toFake: ['setInterval', 'clearInterval']});
			let rootReads = 0;
			worker.use(
				http.post('/v2/element-instances/search', async ({request}) => {
					const {page} = (await request.json()) as {page: {limit: number}};
					if (page.limit === 1) {
						return new HttpResponse(null, {status});
					}
					rootReads++;
					return HttpResponse.json(createQueryElementInstancesResponse());
				}),
			);
			const screen = await renderPage();
			await expect.poll(() => rootReads).toBe(1);
			await userEvent.click(screen.getByRole('button', {name: 'Select ad-hoc instance'}));
			if (status === 403) {
				await expect.element(screen.getByText(/"forbidden":true/)).toBeVisible();
				await userEvent.click(screen.getByRole('button', {name: 'Next'}));
				await userEvent.click(screen.getByRole('button', {name: 'Retry'}));
				await vi.advanceTimersByTimeAsync(15000);
				expect(rootReads).toBe(1);
			} else {
				await expect.poll(() => notificationsStore.notifications.length).toBe(1);
				await expect.element(screen.getByText(/"forbidden":false/)).toBeVisible();
			}
			await expect.element(screen.getByText(/"selected":false/)).toBeVisible();
		},
	);
	it('should re-read a previously forbidden cached instance after returning to it', async ({worker}) => {
		let denied = true;
		let reads = 0;
		worker.use(
			http.post('/v2/element-instances/search', async ({request}) => {
				const {filter} = (await request.json()) as {filter: {elementInstanceScopeKey: string}};
				if (filter.elementInstanceScopeKey === instance.processInstanceKey) {
					reads++;
					if (denied) {
						return new HttpResponse(null, {status: 403});
					}
				}
				return HttpResponse.json(createQueryElementInstancesResponse());
			}),
		);
		const screen = await renderPage();
		await expect.element(screen.getByText(/"forbidden":true/)).toBeVisible();
		denied = false;
		await userEvent.click(screen.getByRole('button', {name: 'Instance'}));
		await expect.element(screen.getByText(/"ready":true/)).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Instance'}));
		await expect.poll(() => reads).toBe(2);
		await expect.element(screen.getByText(/"forbidden":false/)).toBeVisible();
		await expect.element(screen.getByText(/"ready":true/)).toBeVisible();
	});
	it('should retain windows and controls through remount and reset on instance change', async ({worker}) => {
		const requests = vi.fn(() => HttpResponse.json(createQueryElementInstancesResponse([child])));
		worker.use(http.post('/v2/element-instances/search', requests));
		const screen = await renderPage();
		await expect.element(screen.getByText(/"first":/)).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Timestamps'}));
		await userEvent.click(screen.getByRole('button', {name: 'Remount'}));
		await userEvent.click(screen.getByRole('button', {name: 'Remount'}));
		await expect.element(screen.getByText(/"timestamps":true/)).toBeVisible();
		expect(requests).toHaveBeenCalledTimes(1);
		await userEvent.click(screen.getByRole('button', {name: 'Instance'}));
		await expect.element(screen.getByText(/"timestamps":false/)).toBeVisible();
	});
	it.for(['collapse', 'instance'])('should ignore a late forbidden request after %s reset', async (reset, {worker}) => {
		let finish: () => void = () => {};
		onTestFinished(() => finish());
		let calls = 0;
		worker.use(
			http.post('/v2/element-instances/search', async () => {
				if (++calls === 1) {
					await new Promise<void>((resolve) => {
						finish = resolve;
					});
					return new HttpResponse(null, {status: 403});
				}
				return HttpResponse.json(createQueryElementInstancesResponse([child]));
			}),
		);
		const screen = await renderPage();
		await expect.poll(() => calls).toBe(1);
		if (reset === 'collapse') {
			await userEvent.click(screen.getByRole('button', {name: 'Fold'}));
			await userEvent.click(screen.getByRole('button', {name: 'Fold'}));
		} else {
			await userEvent.click(screen.getByRole('button', {name: 'Instance'}));
		}
		await expect.element(screen.getByText(/"first":/)).toBeVisible();
		finish();
		await expect.element(screen.getByText(/"forbidden":false/)).toBeVisible();
	});
	it('should skip hidden polling, recover non-403 errors, and stop on 403', async ({worker}) => {
		vi.useFakeTimers({toFake: ['setInterval', 'clearInterval']});
		const hidden = vi.spyOn(document, 'hidden', 'get').mockReturnValue(true);
		let status = 500;
		const requests = vi.fn(() =>
			status === 200 ? HttpResponse.json(createQueryElementInstancesResponse()) : new HttpResponse(null, {status}),
		);
		worker.use(http.post('/v2/element-instances/search', requests));
		const screen = await renderPage();
		await expect.poll(() => requests.mock.calls.length).toBe(1);
		const rootQueryKey = ['instanceHistory', instance.processInstanceKey, instance.processInstanceKey, 0, 0];
		await expect.poll(() => screen.queryClient.getQueryState(rootQueryKey)?.status).toBe('error');
		await expect.element(screen.getByText(/"historyError":true/)).toBeVisible();
		await vi.advanceTimersByTimeAsync(5000);
		expect(requests).toHaveBeenCalledTimes(1);
		hidden.mockReturnValue(false);
		status = 200;
		await vi.advanceTimersByTimeAsync(5000);
		await expect.poll(() => screen.queryClient.getQueryState(rootQueryKey)?.status).toBe('success');
		status = 403;
		await vi.advanceTimersByTimeAsync(5000);
		await expect.element(screen.getByText(/"forbidden":true/)).toBeVisible();
		await vi.advanceTimersByTimeAsync(15000);
		expect(requests).toHaveBeenCalledTimes(3);
	});
	it.for(['poll', 'retry'])('should clear only recovered nested paging errors after %s', async (recovery, {worker}) => {
		vi.useFakeTimers({toFake: ['setInterval', 'clearInterval']});
		worker.use(
			http.post('/v2/element-instances/search', async ({request}) => {
				const {filter, page} = (await request.json()) as {
					filter: {elementInstanceScopeKey: string};
					page: {from: number};
				};
				if (filter.elementInstanceScopeKey === instance.processInstanceKey) {
					return HttpResponse.json(
						createQueryElementInstancesResponse([createElementInstance({...child, type: 'SUB_PROCESS'})]),
					);
				}
				if (page.from === 50) {
					return new HttpResponse(null, {status: 500});
				}
				return HttpResponse.json(
					createQueryElementInstancesResponse(
						Array.from({length: 100}, (_, index) =>
							createElementInstance({elementInstanceKey: String(index), state: 'COMPLETED'}),
						),
						151,
					),
				);
			}),
		);
		const screen = await renderPage();
		await expect.element(screen.getByText(/"first":/)).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Expand nested'}));
		await expect.element(screen.getByText(/"nestedReady":true/)).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Next nested'}));
		await expect.element(screen.getByText(/"nestedError":true/)).toBeVisible();
		if (recovery === 'poll') {
			await vi.advanceTimersByTimeAsync(5000);
		} else {
			await userEvent.click(screen.getByRole('button', {name: 'Retry'}));
		}
		await expect.element(screen.getByText(/"nestedError":false/)).toBeVisible();
	});
	it('should render root and controls and clear selection while enrichment stays optional', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
			mockQueryBatchOperationItemsEndpoint({successResponse: new HttpResponse(null, {status: 500})}),
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([child])),
			}),
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(child)}),
		);
		const screen = await renderWithRouter(() => <Harness tree />, {
			path,
			initialEntry: `/operate/processes/${instance.processInstanceKey}/variables?elementId=task&elementInstanceKey=${child.elementInstanceKey}&isPlaceholder=true&isMultiInstanceBody=true&anchorElementId=anchor&tenantId=tenant`,
		});
		await expect.element(screen.getByRole('button', {name: 'My Process'})).toBeVisible();
		expect(screen.getByRole('button', {name: 'My Process'}).element().getBoundingClientRect().height).toBe(32);
		await userEvent.click(screen.getByText('Execution count'));
		await expect.element(screen.getByRole('switch', {name: 'Execution count'})).toBeChecked();
		await userEvent.click(screen.getByRole('button', {name: 'My Process'}));
		await expect.poll(() => screen.router.state.location.search).toEqual({tenantId: 'tenant'});
	});
	it('should retry XML before reading disabled history and wait for successful recovery', async ({worker}) => {
		let xmlStatus = 500;
		let xmlReads = 0;
		const historyReads = vi.fn(() => HttpResponse.json(createQueryElementInstancesResponse()));
		worker.use(
			http.get(endpoints.getProcessDefinitionXml({processDefinitionKey: instance.processDefinitionKey}).url, () => {
				xmlReads++;
				return xmlStatus === 200 ? HttpResponse.text('') : new HttpResponse(null, {status: xmlStatus});
			}),
			http.post(endpoints.queryElementInstances({}).url, historyReads),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse()),
			}),
		);
		const screen = await renderWithRouter(() => <Harness tree />, {
			path,
			initialEntry: `/operate/processes/${instance.processInstanceKey}/variables`,
		});
		await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();
		expect(historyReads).not.toHaveBeenCalled();

		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));
		await expect.poll(() => xmlReads).toBe(2);
		await expect.poll(() => screen.queryClient.isFetching()).toBe(0);
		expect(historyReads).not.toHaveBeenCalled();

		xmlStatus = 200;
		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));
		await expect.element(screen.getByRole('button', {name: 'My Process'})).toBeVisible();
		expect(historyReads).toHaveBeenCalledTimes(1);
	});
	it('should preserve legacy state gutters, indentation, row focus and selected-item highlighting', async ({
		worker,
	}) => {
		const subprocess = createElementInstance({
			elementInstanceKey: '2251799813800101',
			elementName: 'Subprocess',
			type: 'SUB_PROCESS',
			state: 'COMPLETED',
		});
		const leaf = createElementInstance({
			elementInstanceKey: '2251799813800102',
			elementName: 'Leaf',
			state: 'TERMINATED',
		});
		const nested = createElementInstance({
			elementInstanceKey: '2251799813800103',
			elementName: 'Nested',
			hasIncident: true,
		});
		worker.use(
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
			mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(createQueryElementInstancesResponse())}),
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(leaf)}),
			http.post(endpoints.queryElementInstances({}).url, async ({request}) => {
				const {filter} = (await request.json()) as {filter: {elementInstanceScopeKey: string}};
				return HttpResponse.json(
					createQueryElementInstancesResponse(
						filter.elementInstanceScopeKey === instance.processInstanceKey ? [subprocess, leaf] : [nested],
					),
				);
			}),
		);
		const screen = await renderWithRouter(() => <Harness tree />, {
			path,
			initialEntry: `/operate/processes/${instance.processInstanceKey}/variables`,
		});
		const rootButton = screen.getByRole('button', {name: 'My Process'});
		const subprocessButton = screen.getByRole('button', {name: 'Subprocess', exact: true});
		const leafButton = screen.getByRole('button', {name: 'Leaf', exact: true});
		await expect.element(leafButton).toBeVisible();
		const origin = screen.getByRole('region', {name: 'Instance History'}).element().getBoundingClientRect().left;
		const x = (element: Element) => element.getBoundingClientRect().left - origin;
		expect(x(screen.getByTestId('ACTIVE-icon').element())).toBe(16);
		expect(x(screen.getByRole('button', {name: 'Collapse My Process'}).element())).toBe(32);
		expect(x(rootButton.getByTestId('element-instance-icon').element())).toBe(56);
		expect(x(subprocessButton.getByTestId('element-instance-icon').element())).toBe(80);
		expect(x(leafButton.getByTestId('element-instance-icon').element())).toBe(72);
		await userEvent.click(screen.getByRole('button', {name: 'Expand Subprocess'}));
		const nestedButton = screen.getByRole('button', {name: 'Nested', exact: true});
		await expect.element(nestedButton).toBeVisible();
		expect(x(screen.getByTestId('INCIDENT-icon').element())).toBe(16);
		expect(x(nestedButton.getByTestId('element-instance-icon').element())).toBe(96);

		await userEvent.click(leafButton);
		await expect.element(leafButton).toHaveFocus();
		await expect.element(leafButton).toHaveAttribute('aria-pressed', 'true');
		const leafItem = screen.getByRole('listitem').filter({hasText: /Leaf/, hasNotText: /My Process/});
		const row = leafItem.element().firstElementChild;
		if (!row) {
			throw new Error('Expected a history row');
		}
		expect(getComputedStyle(row).outlineWidth).toBe('2px');
		expect(getComputedStyle(row).outlineColor).toBe('rgb(15, 98, 254)');
		expect(getComputedStyle(row, '::before').width).toBe('4px');
		expect(getComputedStyle(row, '::before').backgroundColor).toBe('rgb(15, 98, 254)');
		expect(leafButton.element().getBoundingClientRect().height).toBe(32);
		await userEvent.click(screen.getByText('End date', {exact: true}));
		await expect.element(leafButton).not.toHaveFocus();
		expect(getComputedStyle(row).outlineStyle).toBe('none');
		expect(getComputedStyle(row, '::before').width).toBe('4px');
	});
	it('should retain 32px history rows for long labels and timestamps in a narrow panel', async ({worker}) => {
		const label = 'Long history element label with several words '.repeat(10).trim();
		worker.use(
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
			mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(createQueryElementInstancesResponse())}),
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryElementInstancesResponse([
						createElementInstance({elementName: label, endDate: '2026-01-15T10:01:00.000Z'}),
					]),
				),
			}),
		);
		const screen = await renderWithRouter(
			() => (
				<div style={{width: '320px'}}>
					<Harness tree />
				</div>
			),
			{path, initialEntry: `/operate/processes/${instance.processInstanceKey}/variables`},
		);
		const rowButton = screen.getByRole('button', {name: label, exact: true});
		await expect.element(rowButton).toBeVisible();
		expect(rowButton.element().getBoundingClientRect().height).toBe(32);
		await userEvent.click(screen.getByText('End date', {exact: true}));
		expect(rowButton.element().getBoundingClientRect().height).toBe(32);
	});
});
