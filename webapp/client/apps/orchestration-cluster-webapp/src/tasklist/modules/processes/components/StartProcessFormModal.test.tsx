/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {StartProcessFormModal} from './StartProcessFormModal';

const FORM_SCHEMA = JSON.stringify({
	components: [
		{
			key: 'customerName',
			label: 'Customer name',
			type: 'textfield',
			defaultValue: 'Jane Doe',
			validate: {required: true},
		},
	],
	type: 'default',
	id: 'start-process-form',
});

describe('<StartProcessFormModal />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render the process name, start form, and modal actions', async () => {
		const screen = await render(
			<StartProcessFormModal
				processDisplayName="Invoice review"
				schema={FORM_SCHEMA}
				isMultiTenancyEnabled={false}
				tenantId="<default>"
				onClose={vi.fn()}
				onSubmit={() => Promise.resolve()}
				onFileUpload={() => Promise.resolve(new Map())}
			/>,
		);

		await expect.element(screen.getByRole('dialog', {name: 'Start process Invoice review'})).toBeVisible();
		await expect.element(screen.getByRole('textbox', {name: 'Customer name'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Copy link'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Cancel'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Start process'})).toBeVisible();
	});

	it('should close from the cancel and close controls', async () => {
		const onClose = vi.fn();
		const firstRender = await render(
			<StartProcessFormModal
				processDisplayName="Invoice review"
				schema={FORM_SCHEMA}
				isMultiTenancyEnabled={false}
				tenantId="<default>"
				onClose={onClose}
				onSubmit={() => Promise.resolve()}
				onFileUpload={() => Promise.resolve(new Map())}
			/>,
		);

		await userEvent.click(firstRender.getByRole('button', {name: 'Close'}));
		expect(onClose).toHaveBeenCalledOnce();
		await firstRender.unmount();

		const secondRender = await render(
			<StartProcessFormModal
				processDisplayName="Invoice review"
				schema={FORM_SCHEMA}
				isMultiTenancyEnabled={false}
				tenantId="<default>"
				onClose={onClose}
				onSubmit={() => Promise.resolve()}
				onFileUpload={() => Promise.resolve(new Map())}
			/>,
		);
		await userEvent.click(secondRender.getByRole('button', {name: 'Cancel'}));

		expect(onClose).toHaveBeenCalledTimes(2);
	});

	it('should copy the current start-form URL', async () => {
		const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();
		const screen = await render(
			<StartProcessFormModal
				processDisplayName="Invoice review"
				schema={FORM_SCHEMA}
				isMultiTenancyEnabled={false}
				tenantId="<default>"
				onClose={vi.fn()}
				onSubmit={() => Promise.resolve()}
				onFileUpload={() => Promise.resolve(new Map())}
			/>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Copy link'}));

		expect(writeText).toHaveBeenCalledWith(window.location.href);
	});

	it('should submit valid form values', async () => {
		const onSubmit = vi.fn(() => Promise.resolve());
		const screen = await render(
			<StartProcessFormModal
				processDisplayName="Invoice review"
				schema={FORM_SCHEMA}
				isMultiTenancyEnabled={false}
				tenantId="<default>"
				onClose={vi.fn()}
				onSubmit={onSubmit}
				onFileUpload={() => Promise.resolve(new Map())}
			/>,
		);

		const customerNameInput = screen.getByRole('textbox', {name: 'Customer name'});
		await expect.element(customerNameInput).toHaveValue('Jane Doe');
		await userEvent.click(screen.getByRole('button', {name: 'Start process'}));

		await expect.poll(() => onSubmit.mock.calls).toEqual([[[{name: 'customerName', value: '"Jane Doe"'}]]]);
	});
});
