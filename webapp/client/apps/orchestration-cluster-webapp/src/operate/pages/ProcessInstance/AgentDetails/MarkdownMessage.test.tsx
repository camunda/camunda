/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {MarkdownMessage} from './MarkdownMessage';

describe('<MarkdownMessage />', () => {
	it('should render GitHub flavored markdown', async () => {
		const screen = await render(
			<MarkdownMessage
				content={[
					'Use `inline` code and ~~old~~ text.',
					'',
					'```js',
					'const a = 1;',
					'```',
					'',
					'| Name | Value |',
					'| ---- | ----- |',
					'| a    | 1     |',
					'',
					'[Docs](https://docs.camunda.io)',
				].join('\n')}
			/>,
		);

		await expect.element(screen.getByRole('code').filter({hasText: 'inline'})).toBeVisible();
		await expect.element(screen.getByRole('deletion')).toHaveTextContent('old');
		await expect.element(screen.getByText('const a = 1;')).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: '1'})).toBeVisible();
		const link = screen.getByRole('link', {name: 'Docs'});
		await expect.element(link).toHaveAttribute('target', '_blank');
		await expect.element(link).toHaveAttribute('rel', 'noopener noreferrer');
	});

	it('should keep the start number of ordered lists', async () => {
		const screen = await render(<MarkdownMessage content={'3. Step three\n4. Step four'} />);

		await expect.element(screen.getByRole('list')).toHaveAttribute('start', '3');
	});

	it('should not render disallowed elements like images', async () => {
		const screen = await render(<MarkdownMessage content="Look ![diagram](https://example.com/a.png) here" />);

		await expect.element(screen.getByText('Look', {exact: false})).toBeVisible();
		await expect.element(screen.getByRole('img')).not.toBeInTheDocument();
	});

	it('should shift heading levels down and cap them at h6', async () => {
		const screen = await render(<MarkdownMessage content={'# Title\n\n## Subtitle\n\n##### Deep'} />);

		await expect.element(screen.getByRole('heading', {name: 'Title', level: 3})).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Subtitle', level: 4})).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Deep', level: 6})).toBeVisible();
	});
});
