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
import type {SetupWorker} from 'msw/browser';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.11';
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
const subprocess = createElementInstance({
	elementId: 'subprocess',
	elementInstanceKey: '2251799813800101',
	elementName: 'Subprocess',
	type: 'SUB_PROCESS',
	state: 'COMPLETED',
});
const leaf = createElementInstance({
	elementId: 'leaf',
	elementInstanceKey: '2251799813800102',
	elementName: 'Leaf',
	state: 'TERMINATED',
});
const nested = createElementInstance({
	elementId: 'nested',
	elementInstanceKey: '2251799813800103',
	elementName: 'Nested',
	hasIncident: true,
});
function row(item: Element) {
	return item.querySelector<HTMLElement>(':scope > .cds--tree-node__label')!;
}
function toggle(item: Element) {
	return row(item).querySelector<HTMLElement>(':scope > .cds--tree-parent-node__toggle')!;
}
function icon(item: Element) {
	return row(item).querySelector<HTMLElement>('[data-testid="element-instance-icon"]')!;
}
function renderTree(
	worker: SetupWorker,
	search = '',
	rootItems: ElementInstance[] = [subprocess, leaf],
	controls = false,
) {
	worker.use(
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
		mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(createQueryElementInstancesResponse())}),
		mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(leaf)}),
		http.post(endpoints.queryElementInstances({}).url, async ({request}) => {
			const {filter} = (await request.json()) as {filter: {elementInstanceScopeKey?: string}};
			return HttpResponse.json(
				createQueryElementInstancesResponse(
					filter.elementInstanceScopeKey === instance.processInstanceKey ? rootItems : [nested],
				),
			);
		}),
	);
	return renderWithRouter(() => <Harness tree controls={controls} />, {
		path,
		initialEntry: `/operate/processes/${instance.processInstanceKey}/variables${search ? `?${search}` : ''}`,
	});
}
function CollapseControl() {
	const history = useInstanceHistory();
	return (
		<button type="button" onClick={() => history.toggle(subprocess.elementInstanceKey, instance.processInstanceKey)}>
			Collapse subprocess
		</button>
	);
}
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
function Harness({tree = false, controls = false}: {tree?: boolean; controls?: boolean}) {
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
				{controls && <CollapseControl />}
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
		const rootItem = screen.getByRole('treeitem', {name: 'My Process'});
		await expect.element(rootItem).toBeVisible();
		expect(row(rootItem.element()).getBoundingClientRect().height).toBe(32);
		await userEvent.click(screen.getByText('Execution count'));
		await expect.element(screen.getByRole('switch', {name: 'Execution count'})).toBeChecked();
		await userEvent.click(row(rootItem.element()));
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
		await expect.element(screen.getByRole('treeitem', {name: 'My Process'})).toBeVisible();
		expect(historyReads).toHaveBeenCalledTimes(1);
	});
	it('should preserve legacy state gutters, indentation, row focus and selected-item highlighting', async ({
		worker,
	}) => {
		const screen = await renderTree(worker);
		const tree = screen.getByRole('tree', {name: 'Instance History'});
		const rootItem = screen.getByRole('treeitem', {name: 'My Process'});
		const subprocessItem = screen.getByRole('treeitem', {name: 'Subprocess', exact: true});
		const leafItem = screen.getByRole('treeitem', {name: 'Leaf', exact: true});
		await expect.element(leafItem).toBeVisible();
		const origin = screen.getByRole('region', {name: 'Instance History'}).element().getBoundingClientRect().left;
		const x = (element: Element) => element.getBoundingClientRect().left - origin;
		expect(x(screen.getByTestId('ACTIVE-icon').element())).toBe(16);
		expect(x(toggle(rootItem.element()))).toBe(32);
		expect(x(icon(rootItem.element()))).toBe(56);
		expect(x(icon(subprocessItem.element()))).toBe(80);
		expect(x(icon(leafItem.element()))).toBe(72);
		await userEvent.click(toggle(subprocessItem.element()));
		const nestedItem = screen.getByRole('treeitem', {name: 'Nested', exact: true});
		await expect.element(nestedItem).toBeVisible();
		await expect.element(subprocessItem).toHaveAttribute('aria-expanded', 'true');
		expect(screen.router.state.location.search).toEqual({});
		expect(x(screen.getByTestId('INCIDENT-icon').element())).toBe(16);
		expect(x(icon(nestedItem.element()))).toBe(96);
		expect(tree.element().querySelectorAll('button')).toHaveLength(0);

		await userEvent.click(leafItem);
		await expect.element(leafItem).toHaveFocus();
		await expect.element(leafItem).toHaveAttribute('aria-selected', 'true');
		await expect.poll(() => screen.router.state.location.search).toMatchObject({elementId: leaf.elementId});
		const leafRow = row(leafItem.element());
		expect(getComputedStyle(leafRow).outlineWidth).toBe('2px');
		expect(getComputedStyle(leafRow).outlineColor).toBe('rgb(15, 98, 254)');
		expect(getComputedStyle(leafRow, '::before').width).toBe('4px');
		expect(getComputedStyle(leafRow, '::before').backgroundColor).toBe('rgb(15, 98, 254)');
		expect(leafRow.getBoundingClientRect().height).toBe(32);
		await userEvent.click(screen.getByText('End date', {exact: true}));
		await expect.element(leafItem).not.toHaveFocus();
		expect(getComputedStyle(leafRow).outlineStyle).toBe('none');
		expect(getComputedStyle(leafRow, '::before').width).toBe('4px');
		await expect.element(rootItem).toHaveAttribute('aria-selected', 'false');
	});
	it('should highlight every visible instance for an element-only selection', async ({worker}) => {
		const screen = await renderTree(worker, `elementId=${leaf.elementId}`, [
			leaf,
			createElementInstance({...leaf, elementInstanceKey: '2251799813800104', elementName: 'Leaf again'}),
		]);
		const first = screen.getByRole('treeitem', {name: 'Leaf', exact: true});
		const second = screen.getByRole('treeitem', {name: 'Leaf again', exact: true});
		await expect.element(second).toHaveAttribute('aria-selected', 'true');
		await expect.element(first).toHaveAttribute('aria-selected', 'true');
		await expect.element(screen.getByRole('treeitem', {name: 'My Process'})).toHaveAttribute('aria-selected', 'false');
		expect(getComputedStyle(row(second.element()), '::before').width).toBe('4px');
	});
	it('should navigate the native tree with a single roving tab stop without selecting on focus', async ({worker}) => {
		const screen = await renderTree(worker);
		const tree = screen.getByRole('tree', {name: 'Instance History'});
		const rootItem = screen.getByRole('treeitem', {name: 'My Process'});
		const subprocessItem = screen.getByRole('treeitem', {name: 'Subprocess', exact: true});
		const leafItem = screen.getByRole('treeitem', {name: 'Leaf', exact: true});
		const nestedItem = screen.getByRole('treeitem', {name: 'Nested', exact: true});
		await expect.element(leafItem).toBeVisible();
		await expect.element(rootItem).toHaveAttribute('aria-expanded', 'true');
		await expect.element(subprocessItem).toHaveAttribute('aria-expanded', 'false');
		await expect.element(leafItem).not.toHaveAttribute('aria-expanded');
		const tabStops = () =>
			Array.from(tree.element().querySelectorAll('[tabindex="0"]'), (element) => element.getAttribute('aria-label'));
		expect(tabStops()).toEqual(['My Process']);

		screen.getByRole('switch', {name: 'Execution count'}).element().focus();
		await userEvent.tab();
		await expect.element(rootItem).toHaveFocus();
		await userEvent.keyboard('{ArrowDown}');
		await expect.element(subprocessItem).toHaveFocus();
		expect(tabStops()).toEqual(['Subprocess']);
		const focusedRow = row(subprocessItem.element());
		expect(getComputedStyle(focusedRow).outlineWidth).toBe('2px');
		expect(getComputedStyle(focusedRow).outlineColor).toBe('rgb(15, 98, 254)');
		expect(focusedRow.getBoundingClientRect().height).toBe(32);

		await userEvent.keyboard('{ArrowRight}');
		await expect.element(subprocessItem).toHaveAttribute('aria-expanded', 'true');
		await expect.element(nestedItem).toBeVisible();
		await expect.element(subprocessItem).toHaveFocus();
		await userEvent.keyboard('{ArrowRight}');
		await expect.element(nestedItem).toHaveFocus();
		await userEvent.keyboard('{ArrowLeft}');
		await expect.element(subprocessItem).toHaveFocus();
		await userEvent.keyboard('{ArrowLeft}');
		await expect.element(subprocessItem).toHaveAttribute('aria-expanded', 'false');
		await expect.element(nestedItem).not.toBeInTheDocument();
		await userEvent.keyboard('{ArrowLeft}');
		await expect.element(rootItem).toHaveFocus();
		await userEvent.keyboard('{End}');
		await expect.element(leafItem).toHaveFocus();
		await userEvent.keyboard('{ArrowUp}');
		await expect.element(subprocessItem).toHaveFocus();
		await userEvent.keyboard('{Home}');
		await expect.element(rootItem).toHaveFocus();
		await userEvent.keyboard('{End}');
		expect(screen.router.state.location.search).toEqual({});
		await expect.element(rootItem).toHaveAttribute('aria-selected', 'true');
		await expect.element(leafItem).toHaveAttribute('aria-selected', 'false');

		await userEvent.tab({shift: true});
		await expect.element(screen.getByRole('switch', {name: 'Execution count'})).toHaveFocus();
		await userEvent.tab();
		await expect.element(leafItem).toHaveFocus();
		expect(tabStops()).toEqual(['Leaf']);

		await userEvent.keyboard(' ');
		await expect
			.poll(() => screen.router.state.location.search)
			.toEqual({elementId: leaf.elementId, elementInstanceKey: leaf.elementInstanceKey});
		await expect.element(leafItem).toHaveAttribute('aria-selected', 'true');
		await expect.element(leafItem).toHaveFocus();
		await userEvent.keyboard('{ArrowUp}{Enter}');
		await expect
			.poll(() => screen.router.state.location.search)
			.toEqual({elementId: subprocess.elementId, elementInstanceKey: subprocess.elementInstanceKey});
		await expect.element(subprocessItem).toHaveAttribute('aria-expanded', 'true');
		await expect.element(subprocessItem).toHaveAttribute('aria-selected', 'true');
		await expect.element(subprocessItem).toHaveAttribute('aria-current', 'true');
		await expect.element(leafItem).toHaveAttribute('aria-selected', 'false');
		await userEvent.keyboard('{Home}{Enter}');
		await expect.poll(() => screen.router.state.location.search).toEqual({});
		await expect.element(rootItem).toHaveAttribute('aria-selected', 'true');
		await expect.element(rootItem).toHaveAttribute('aria-expanded', 'false');
		await expect.element(leafItem).not.toBeInTheDocument();
	});
	it('should move focus to the nearest visible ancestor when the focused item disappears', async ({worker}) => {
		const screen = await renderTree(worker, '', undefined, true);
		const subprocessItem = screen.getByRole('treeitem', {name: 'Subprocess', exact: true});
		await expect.element(subprocessItem).toBeVisible();
		await userEvent.click(toggle(subprocessItem.element()));
		const nestedItem = screen.getByRole('treeitem', {name: 'Nested', exact: true});
		await userEvent.click(nestedItem);
		await expect.element(nestedItem).toHaveFocus();
		screen
			.getByRole('button', {name: 'Collapse subprocess'})
			.element()
			.dispatchEvent(new MouseEvent('click', {bubbles: true}));
		await expect.element(nestedItem).not.toBeInTheDocument();
		await expect.element(subprocessItem).toHaveFocus();
		expect(subprocessItem.element().tabIndex).toBe(0);
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
		const item = screen.getByRole('treeitem', {name: label, exact: true});
		await expect.element(item).toBeVisible();
		expect(row(item.element()).getBoundingClientRect().height).toBe(32);
		await userEvent.click(screen.getByText('End date', {exact: true}));
		expect(row(item.element()).getBoundingClientRect().height).toBe(32);
		expect(screen.getByRole('tree').element().querySelectorAll('button')).toHaveLength(0);
	});
});
