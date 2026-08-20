/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, it, expect} from 'vitest';
import {InstanceHeader} from './InstanceHeader';
import {InstanceHeaderSkeleton} from './InstanceHeaderSkeleton';

describe('<InstanceHeader />', () => {
	it('should render the instance name, header columns and body columns', async () => {
		const screen = await render(
			<InstanceHeader
				state="ACTIVE"
				instanceName="orderProcess"
				headerColumns={['Process Instance Key', 'Version']}
				bodyColumns={[{title: '2251799813685467', content: '2251799813685467'}, {content: '1'}]}
			/>,
		);

		await expect.element(screen.getByText('orderProcess')).toBeVisible();
		await expect.element(screen.getByText('Process Instance Key')).toBeVisible();
		await expect.element(screen.getByText('2251799813685467')).toBeVisible();
		await expect.element(screen.getByTestId('ACTIVE-icon')).toBeVisible();
	});

	it('should show the incidents count when there is at least one incident', async () => {
		const screen = await render(
			<InstanceHeader
				state="INCIDENT"
				instanceName="orderProcess"
				incidentsCount={3}
				headerColumns={[]}
				bodyColumns={[]}
			/>,
		);

		await expect.element(screen.getByText('3 incidents')).toBeVisible();
	});

	it('should not show the incidents count when there are no incidents', async () => {
		const screen = await render(
			<InstanceHeader state="ACTIVE" instanceName="orderProcess" headerColumns={[]} bodyColumns={[]} />,
		);

		expect(screen.getByText(/incidents?$/).elements()).toHaveLength(0);
	});

	it('should show the name subtitle', async () => {
		const screen = await render(
			<InstanceHeader
				state="ACTIVE"
				instanceName="orderProcess"
				nameSubtitle="Waiting"
				headerColumns={[]}
				bodyColumns={[]}
			/>,
		);

		await expect.element(screen.getByText('Waiting')).toBeVisible();
	});

	it('should skip hidden body columns', async () => {
		const screen = await render(
			<InstanceHeader
				state="ACTIVE"
				instanceName="orderProcess"
				headerColumns={[]}
				bodyColumns={[{content: 'hidden-content', hidden: true}]}
			/>,
		);

		expect(screen.getByText('hidden-content').elements()).toHaveLength(0);
	});

	it('should render additional content', async () => {
		const screen = await render(
			<InstanceHeader
				state="ACTIVE"
				instanceName="orderProcess"
				headerColumns={[]}
				bodyColumns={[]}
				additionalContent={<button>Cancel Instance</button>}
			/>,
		);

		await expect.element(screen.getByRole('button', {name: 'Cancel Instance'})).toBeVisible();
	});
});

describe('<InstanceHeaderSkeleton />', () => {
	it('should render a header column per entry', async () => {
		const screen = await render(
			<InstanceHeaderSkeleton
				headerColumns={[
					{name: 'Process Instance Key', skeletonWidth: '136px'},
					{name: 'Version', skeletonWidth: '34px'},
				]}
			/>,
		);

		await expect.element(screen.getByText('Process Instance Key')).toBeVisible();
		await expect.element(screen.getByText('Version')).toBeVisible();
		await expect.element(screen.getByTestId('instance-header-skeleton')).toBeVisible();
	});
});
