/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {mockGetProcessInstanceCallHierarchyEndpoint} from '#/shared-test-modules/mock-handlers';
import {createCallHierarchy} from '#/shared-test-modules/api-mocks/call-hierarchy';
import {Operations} from './Operations';

const PROCESS_INSTANCE_KEY = 'instance_1';

function getWrapper() {
	const queryClient = new QueryClient({
		defaultOptions: {
			queries: {retry: false},
		},
	});

	const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
		<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
	);

	return Wrapper;
}

describe('<Operations />', () => {
	it('should render retry, cancel, modify and delete buttons', async ({worker}) => {
		worker.use(mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json([])}));

		const screen = await render(
			<Operations
				operations={[
					{type: 'RESOLVE_INCIDENT', onExecute: vi.fn()},
					{type: 'CANCEL_PROCESS_INSTANCE', onExecute: vi.fn()},
					{type: 'ENTER_MODIFICATION_MODE', onExecute: vi.fn()},
					{type: 'DELETE_PROCESS_INSTANCE', onExecute: vi.fn()},
				]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
			/>,
			{wrapper: getWrapper()},
		);

		await expect
			.element(screen.getByRole('button', {name: `Retry Instance ${PROCESS_INSTANCE_KEY}`, exact: true}))
			.toBeVisible();
		await expect
			.element(screen.getByRole('button', {name: `Cancel Instance ${PROCESS_INSTANCE_KEY}`, exact: true}))
			.toBeVisible();
		await expect
			.element(screen.getByRole('button', {name: `Modify Instance ${PROCESS_INSTANCE_KEY}`, exact: true}))
			.toBeVisible();
		await expect
			.element(screen.getByRole('button', {name: `Delete Instance ${PROCESS_INSTANCE_KEY}`, exact: true}))
			.toBeVisible();
	});

	it('should render suspend button', async () => {
		const screen = await render(
			<Operations
				operations={[{type: 'SUSPEND_PROCESS_INSTANCE', onExecute: vi.fn()}]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
			/>,
			{wrapper: getWrapper()},
		);

		await expect
			.element(screen.getByRole('button', {name: `Suspend Instance ${PROCESS_INSTANCE_KEY}`, exact: true}))
			.toBeVisible();
	});

	it('should render resume button', async () => {
		const screen = await render(
			<Operations
				operations={[{type: 'RESUME_PROCESS_INSTANCE', onExecute: vi.fn()}]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
			/>,
			{wrapper: getWrapper()},
		);

		await expect
			.element(screen.getByRole('button', {name: `Resume Instance ${PROCESS_INSTANCE_KEY}`, exact: true}))
			.toBeVisible();
	});

	it('should render no buttons when operations array is empty', async () => {
		const screen = await render(<Operations operations={[]} processInstanceKey={PROCESS_INSTANCE_KEY} />, {
			wrapper: getWrapper(),
		});

		expect(
			screen.getByRole('button', {name: `Retry Instance ${PROCESS_INSTANCE_KEY}`, exact: true}).elements(),
		).toHaveLength(0);
		expect(
			screen.getByRole('button', {name: `Cancel Instance ${PROCESS_INSTANCE_KEY}`, exact: true}).elements(),
		).toHaveLength(0);
		expect(
			screen.getByRole('button', {name: `Modify Instance ${PROCESS_INSTANCE_KEY}`, exact: true}).elements(),
		).toHaveLength(0);
		expect(
			screen.getByRole('button', {name: `Delete Instance ${PROCESS_INSTANCE_KEY}`, exact: true}).elements(),
		).toHaveLength(0);
		expect(
			screen.getByRole('button', {name: `Suspend Instance ${PROCESS_INSTANCE_KEY}`, exact: true}).elements(),
		).toHaveLength(0);
		expect(
			screen.getByRole('button', {name: `Resume Instance ${PROCESS_INSTANCE_KEY}`, exact: true}).elements(),
		).toHaveLength(0);
	});

	it('should execute resolve incident operation', async () => {
		const onExecute = vi.fn();

		const screen = await render(
			<Operations operations={[{type: 'RESOLVE_INCIDENT', onExecute}]} processInstanceKey={PROCESS_INSTANCE_KEY} />,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: /retry instance/i}));

		expect(onExecute).toHaveBeenCalled();
	});

	it('should execute cancel operation exactly once, without also triggering cancellation', async ({worker}) => {
		worker.use(mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json([])}));
		const onExecute = vi.fn();

		const screen = await render(
			<Operations
				operations={[{type: 'CANCEL_PROCESS_INSTANCE', onExecute}]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
			/>,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: /cancel instance/i}));
		const applyButton = screen.getByRole('button', {name: 'Apply', exact: true});
		await expect.element(applyButton).toBeEnabled();
		await userEvent.click(applyButton);

		expect(onExecute).toHaveBeenCalledOnce();
		await expect.element(screen.getByTestId('confirm-cancellation-modal')).not.toBeInTheDocument();
	});

	it('should execute delete operation exactly once, without also triggering cancellation', async () => {
		const onExecute = vi.fn();

		const screen = await render(
			<Operations
				operations={[{type: 'DELETE_PROCESS_INSTANCE', onExecute}]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
			/>,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: /delete instance/i}));
		await userEvent.click(screen.getByRole('button', {name: /^delete$/i}));

		expect(onExecute).toHaveBeenCalledOnce();
		await expect.element(screen.getByTestId('confirm-deletion-modal')).not.toBeInTheDocument();
	});

	it('should execute modify operation', async () => {
		const onExecute = vi.fn();

		const screen = await render(
			<Operations
				operations={[{type: 'ENTER_MODIFICATION_MODE', onExecute}]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
			/>,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: /modify instance/i}));

		expect(onExecute).toHaveBeenCalled();
	});

	it('should execute suspend operation', async () => {
		const onExecute = vi.fn();

		const screen = await render(
			<Operations
				operations={[{type: 'SUSPEND_PROCESS_INSTANCE', onExecute}]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
			/>,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: /suspend instance/i}));

		expect(onExecute).toHaveBeenCalled();
	});

	it('should execute resume operation', async () => {
		const onExecute = vi.fn();

		const screen = await render(
			<Operations
				operations={[{type: 'RESUME_PROCESS_INSTANCE', onExecute}]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
			/>,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: /resume instance/i}));

		expect(onExecute).toHaveBeenCalled();
	});

	it('should show loading state', async () => {
		const screen = await render(
			<Operations
				operations={[{type: 'CANCEL_PROCESS_INSTANCE', onExecute: vi.fn()}]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
				isLoading
			/>,
			{wrapper: getWrapper()},
		);

		await expect.element(screen.getByTestId('operation-spinner')).toBeVisible();
	});

	it('should hide loading state', async () => {
		const screen = await render(
			<Operations
				operations={[{type: 'CANCEL_PROCESS_INSTANCE', onExecute: vi.fn()}]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
				isLoading={false}
			/>,
			{wrapper: getWrapper()},
		);

		expect(screen.getByTestId('operation-spinner').elements()).toHaveLength(0);
	});

	it('should show cancel confirmation modal', async ({worker}) => {
		worker.use(mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json([])}));

		const screen = await render(
			<Operations
				operations={[{type: 'CANCEL_PROCESS_INSTANCE', onExecute: vi.fn()}]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
			/>,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: /cancel instance/i}));

		const modalText = `About to cancel Instance ${PROCESS_INSTANCE_KEY}. In case there are called instances, these will be canceled too.`;
		await expect.element(screen.getByText(modalText)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Apply', exact: true})).toBeEnabled();
		await expect.element(screen.getByRole('button', {name: 'Cancel', exact: true})).toBeVisible();
	});

	it('should disable cancellation while the call hierarchy is loading', async ({worker}) => {
		worker.use(
			mockGetProcessInstanceCallHierarchyEndpoint({
				successResponse: HttpResponse.json([]),
				delay: 'infinite',
			}),
		);
		const onExecute = vi.fn();

		const screen = await render(
			<Operations
				operations={[{type: 'CANCEL_PROCESS_INSTANCE', onExecute}]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
			/>,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: /cancel instance/i}));

		await expect.element(screen.getByText('Checking whether this instance can be canceled...')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Apply', exact: true})).toBeDisabled();
		expect(onExecute).not.toHaveBeenCalled();
	});

	it('should disable cancellation when the call hierarchy cannot be loaded', async ({worker}) => {
		worker.use(
			mockGetProcessInstanceCallHierarchyEndpoint({
				successResponse: HttpResponse.json({}, {status: 500}),
			}),
		);
		const onExecute = vi.fn();

		const screen = await render(
			<Operations
				operations={[{type: 'CANCEL_PROCESS_INSTANCE', onExecute}]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
			/>,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: /cancel instance/i}));

		await expect
			.element(screen.getByRole('alert'))
			.toMatchTextContent("Couldn't check cancellation availability. Close this dialog and try again.");
		await expect.element(screen.getByRole('button', {name: 'Apply', exact: true})).toBeDisabled();
		expect(onExecute).not.toHaveBeenCalled();
	});

	it('should show delete confirmation modal', async () => {
		const screen = await render(
			<Operations
				operations={[{type: 'DELETE_PROCESS_INSTANCE', onExecute: vi.fn()}]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
			/>,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: /delete instance/i}));

		const modalText = `About to delete Instance ${PROCESS_INSTANCE_KEY}.`;
		await expect.element(screen.getByText(modalText)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: /^delete$/i})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Cancel', exact: true})).toBeVisible();
	});

	it('should show root instance warning modal when call hierarchy has parents', async ({worker}) => {
		worker.use(
			mockGetProcessInstanceCallHierarchyEndpoint({
				successResponse: HttpResponse.json([
					createCallHierarchy({processInstanceKey: '3', processDefinitionName: 'some root process'}),
					createCallHierarchy({processInstanceKey: '2', processDefinitionName: 'some parent process'}),
				]),
			}),
		);

		const screen = await render(
			<Operations
				operations={[{type: 'CANCEL_PROCESS_INSTANCE', onExecute: vi.fn()}]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
			/>,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: /cancel instance/i}));

		await expect.element(screen.getByTestId('passive-cancellation-modal')).toBeVisible();
		await expect.element(screen.getByText(/to cancel this instance, the root instance/i)).toBeVisible();
		await expect.element(screen.getByRole('link', {name: '3'})).toHaveAttribute('href', '/operate/processes/3');
		expect(screen.getByRole('button', {name: 'Cancel', exact: true}).elements()).toHaveLength(0);
		expect(screen.getByRole('button', {name: 'Apply', exact: true}).elements()).toHaveLength(0);
	});

	it('should never drop focus to the document body while the call hierarchy check resolves', async ({worker}) => {
		worker.use(
			mockGetProcessInstanceCallHierarchyEndpoint({
				successResponse: HttpResponse.json([
					createCallHierarchy({processInstanceKey: '3', processDefinitionName: 'some root process'}),
				]),
				delay: 150,
			}),
		);

		const focusedElements: Element[] = [];
		const onFocusIn = (event: FocusEvent) => {
			if (event.target instanceof Element) {
				focusedElements.push(event.target);
			}
		};
		document.addEventListener('focusin', onFocusIn);

		try {
			const screen = await render(
				<Operations
					operations={[{type: 'CANCEL_PROCESS_INSTANCE', onExecute: vi.fn()}]}
					processInstanceKey={PROCESS_INSTANCE_KEY}
				/>,
				{wrapper: getWrapper()},
			);

			await userEvent.click(screen.getByRole('button', {name: /cancel instance/i}));

			// While the call hierarchy request is pending, the AlertDialog is showing and should
			// have moved focus into itself already (never left on the trigger button nor on body).
			await expect.element(screen.getByTestId('confirm-cancellation-modal')).toBeVisible();
			expect(document.activeElement).not.toBe(document.body);
			expect(document.activeElement?.closest('[data-testid="confirm-cancellation-modal"]')).not.toBeNull();

			// Once the query resolves, the AlertDialog unmounts and the passive Dialog mounts in its
			// place; focus must land inside the new dialog, never regressing to the document body.
			await expect.element(screen.getByTestId('passive-cancellation-modal')).toBeVisible();
			expect(document.activeElement).not.toBe(document.body);
			expect(document.activeElement?.closest('[data-testid="passive-cancellation-modal"]')).not.toBeNull();

			// No focusin event observed anywhere during the whole transition targeted body directly.
			expect(focusedElements).not.toContain(document.body);
		} finally {
			document.removeEventListener('focusin', onFocusIn);
		}
	});

	it('should keep the root instance warning modal open when clicking outside it', async ({worker}) => {
		worker.use(
			mockGetProcessInstanceCallHierarchyEndpoint({
				successResponse: HttpResponse.json([
					createCallHierarchy({processInstanceKey: '3', processDefinitionName: 'some root process'}),
				]),
			}),
		);

		const screen = await render(
			<Operations
				operations={[{type: 'CANCEL_PROCESS_INSTANCE', onExecute: vi.fn()}]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
			/>,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: /cancel instance/i}));
		await expect.element(screen.getByTestId('passive-cancellation-modal')).toBeVisible();

		await userEvent.click(document.querySelector<HTMLElement>('[data-slot="dialog-overlay"]')!, {
			position: {x: 5, y: 5},
		});

		await expect.element(screen.getByTestId('passive-cancellation-modal')).toBeVisible();
	});

	it('should not trigger callbacks when disabled', async () => {
		const onExecute = vi.fn();

		const screen = await render(
			<Operations
				operations={[
					{type: 'CANCEL_PROCESS_INSTANCE', onExecute, disabled: true},
					{type: 'DELETE_PROCESS_INSTANCE', onExecute, disabled: true},
					{type: 'RESOLVE_INCIDENT', onExecute, disabled: true},
					{type: 'ENTER_MODIFICATION_MODE', onExecute, disabled: true},
					{type: 'SUSPEND_PROCESS_INSTANCE', onExecute, disabled: true},
					{type: 'RESUME_PROCESS_INSTANCE', onExecute, disabled: true},
				]}
				processInstanceKey={PROCESS_INSTANCE_KEY}
			/>,
			{wrapper: getWrapper()},
		);

		const cancelButton = screen.getByRole('button', {name: /cancel instance/i});
		const deleteButton = screen.getByRole('button', {name: /delete instance/i});
		const retryButton = screen.getByRole('button', {name: /retry instance/i});
		const modifyButton = screen.getByRole('button', {name: /modify instance/i});
		const suspendButton = screen.getByRole('button', {name: /suspend instance/i});
		const resumeButton = screen.getByRole('button', {name: /resume instance/i});

		await expect.element(cancelButton).toBeDisabled();
		await expect.element(deleteButton).toBeDisabled();
		await expect.element(retryButton).toBeDisabled();
		await expect.element(modifyButton).toBeDisabled();
		await expect.element(suspendButton).toBeDisabled();
		await expect.element(resumeButton).toBeDisabled();

		expect(onExecute).not.toHaveBeenCalled();
	});
});
