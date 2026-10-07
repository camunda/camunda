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
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {Task} from './Task';

const currentUser = createCurrentUser({username: 'demo'});

type TaskProps = React.ComponentProps<typeof Task>;

const baseProps: TaskProps = {
	userTaskKey: 'task-42',
	displayName: 'Review invoice',
	businessId: null,
	processDisplayName: 'Invoice process',
	assignee: null,
	creationDate: '2024-01-06T12:00:00.000Z',
	followUpDate: null,
	dueDate: null,
	completionDate: null,
	priority: 50,
	currentUser,
};

const BorderBox = () => <style>{'*, *::before, *::after {box-sizing: border-box}'}</style>;

/**
 * Elements sticking out of the card, either by their box or by their content spilling
 * out visibly. Content that is clipped (truncated) is not reported.
 */
const getOverflowingElements = (card: Element) => {
	const containerRight = (card.closest('article') ?? card).getBoundingClientRect().right;

	return [card, ...card.querySelectorAll('*')].filter((element) => {
		const spillsOut =
			getComputedStyle(element).overflowX === 'visible' && element.scrollWidth > element.clientWidth + 1;

		return element.getBoundingClientRect().right > containerRight + 1 || spillsOut;
	});
};

const TestTask: React.FC<Partial<TaskProps>> = (props) => <Task {...baseProps} {...props} />;

describe('<Task />', () => {
	it('should render the task display name and process name', async () => {
		const screen = await renderWithRouter(() => <TestTask />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByText('Review invoice')).toBeVisible();
		await expect.element(screen.getByText('Invoice process')).toBeVisible();
	});

	it('should render a business id when provided', async () => {
		const screen = await renderWithRouter(() => <TestTask businessId="order-123" />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByText('order-123')).toBeVisible();
	});

	it('should render a link with an accessible label for an unassigned task', async () => {
		const screen = await renderWithRouter(() => <TestTask assignee={null} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByRole('link', {name: 'Unassigned task: Review invoice'})).toBeVisible();
	});

	it('should render an "assigned to me" label', async () => {
		const screen = await renderWithRouter(() => <TestTask assignee={currentUser.username} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByRole('link', {name: 'Task assigned to me: Review invoice'})).toBeVisible();
	});

	it('should render an "assigned task" label', async () => {
		const screen = await renderWithRouter(() => <TestTask assignee="john.doe" />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByRole('link', {name: 'Assigned task: Review invoice'})).toBeVisible();
	});

	it('should render the priority label', async () => {
		const screen = await renderWithRouter(() => <TestTask priority={80} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByText('Critical', {exact: true})).toBeVisible();
	});

	it('should not render the priority label', async () => {
		const screen = await renderWithRouter(() => <TestTask priority={null} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByText('Critical')).not.toBeInTheDocument();
		await expect.element(screen.getByText('High')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Medium')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Low')).not.toBeInTheDocument();
	});

	it('should keep all labels inside the card when the assignee name is very long', async () => {
		const screen = await renderWithRouter(
			() => (
				<div style={{width: 280}}>
					<BorderBox />
					<TestTask
						assignee="a.very.long.assignee.username@example-company.com"
						priority={80}
						dueDate="2030-01-06T12:00:00.000Z"
					/>
				</div>
			),
			{path: '/tasklist/$userTaskKey', initialEntry: '/tasklist/other-task'},
		);

		const card = screen.getByRole('link').element();
		expect(getOverflowingElements(card)).toEqual([]);
	});

	it('should keep the card within its width when the names are very long', async () => {
		const longValue = 'unbrokenvalue'.repeat(12);
		const screen = await renderWithRouter(
			() => (
				<div style={{width: 280}}>
					<BorderBox />
					<TestTask displayName={longValue} processDisplayName={longValue} businessId={longValue} />
				</div>
			),
			{path: '/tasklist/$userTaskKey', initialEntry: '/tasklist/other-task'},
		);

		const card = screen.getByRole('link').element();

		expect(getOverflowingElements(card)).toEqual([]);
	});

	it('should keep the card within its width when the panel is very narrow', async () => {
		const screen = await renderWithRouter(
			() => (
				<div style={{width: 120}}>
					<BorderBox />
					<TestTask dueDate="2030-01-06T12:00:00.000Z" assignee="john.doe" priority={80} />
				</div>
			),
			{path: '/tasklist/$userTaskKey', initialEntry: '/tasklist/other-task'},
		);

		const card = screen.getByRole('link').element();

		expect(getOverflowingElements(card)).toEqual([]);
	});

	it.for([
		{path: '/tasklist/$userTaskKey' as const, initialEntry: '/tasklist/task-42'},
		{path: '/tasklist/$userTaskKey/process' as const, initialEntry: '/tasklist/task-42/process'},
		{path: '/tasklist/$userTaskKey/history' as const, initialEntry: '/tasklist/task-42/history'},
	])('should mark the task as selected at $initialEntry', async ({path, initialEntry}) => {
		const screen = await renderWithRouter(() => <TestTask />, {path, initialEntry});

		await expect
			.element(screen.getByRole('link', {name: 'Unassigned task: Review invoice'}))
			.toHaveAttribute('aria-current', 'page');
	});

	it('should not mark a different task as selected', async () => {
		const screen = await renderWithRouter(() => <TestTask />, {
			path: '/tasklist/$userTaskKey/process',
			initialEntry: '/tasklist/other-task/process',
		});

		await expect
			.element(screen.getByRole('link', {name: 'Unassigned task: Review invoice'}))
			.not.toHaveAttribute('aria-current');
	});
});
