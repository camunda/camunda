/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {CheckmarkFilledIcon, Stack} from '#/shared/design-system-compat';
import {Button, useMediaQuery} from '@camunda/design-system';
import {ArrowLeft} from 'lucide-react';
import {useNavigate} from '@tanstack/react-router';
import {AssigneeTag} from '#/tasklist/modules/available-tasks/components/AssigneeTag';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
import {ActiveTransitionLoadingText} from './ActiveTransitionLoadingText';
import styles from './TaskDetailsHeader.module.scss';
import layoutStyles from './taskDetailsLayoutCommon.module.scss';

type Props = {
	taskName: string;
	processName: string;
	assignee: string | null;
	taskState:
		| 'CREATED'
		| 'COMPLETED'
		| 'CANCELED'
		| 'FAILED'
		| 'ASSIGNING'
		| 'UPDATING'
		| 'COMPLETING'
		| 'CANCELING'
		| 'CREATING';
	assignButton: React.ReactNode;
	user: CurrentUser;
};

const TaskDetailsHeader: React.FC<Props> = ({taskName, processName, assignee, taskState, user, assignButton}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	// DS-only: below `md` (--breakpoint-md, 48rem/768px) the task list and
	// task detail are separate single-pane views (see TasksLayoutPage.tsx) —
	// this is the way back to the list.
	const isBelowMd = useMediaQuery('(width < 48rem)');

	function renderRightContent() {
		switch (taskState) {
			case 'COMPLETED':
				return (
					<span
						className={cn(styles.taskStatus, featureFlags.dsTasklistUI && styles.taskStatusDS)}
						data-testid="completion-label"
						title={t('tasklist.taskDetailsTaskCompletedBy')}
					>
						<Stack className={styles.alignItemsCenter} orientation="horizontal" gap={2}>
							<CheckmarkFilledIcon size={16} color="green" />
							{assignee ? (
								<>
									{t('tasklist.taskDetailsTaskCompletedBy') + ' '}
									<span
										className={cn(styles.taskAssignee, featureFlags.dsTasklistUI && styles.taskAssigneeDS)}
										data-testid="assignee"
									>
										<AssigneeTag currentUser={user} assignee={assignee} isShortFormat />
									</span>
								</>
							) : (
								t('tasklist.taskAssignmentStatusCompleted')
							)}
						</Stack>
					</span>
				);
			case 'CREATED':
			case 'CANCELED':
			case 'FAILED':
				return (
					<>
						<span
							className={cn(styles.taskAssignee, featureFlags.dsTasklistUI && styles.taskAssigneeDS)}
							data-testid="assignee"
						>
							<AssigneeTag currentUser={user} assignee={assignee} isShortFormat={false} />
						</span>
						<span
							className={cn(
								styles.assignButtonContainer,
								featureFlags.dsTasklistUI && styles.assignButtonContainerDS,
							)}
						>
							{assignButton}
						</span>
					</>
				);
			case 'UPDATING':
			case 'CANCELING':
				return (
					<>
						<ActiveTransitionLoadingText taskState={taskState} />
						<span
							className={cn(styles.taskAssignee, featureFlags.dsTasklistUI && styles.taskAssigneeDS)}
							data-testid="assignee"
						>
							<AssigneeTag currentUser={user} assignee={assignee} isShortFormat={false} />
						</span>
					</>
				);
			case 'COMPLETING':
				return (
					<span className={styles.taskAssignee} data-testid="assignee">
						<AssigneeTag currentUser={user} assignee={assignee} isShortFormat={false} />
					</span>
				);
			case 'ASSIGNING':
				return <span className={styles.assignButtonContainer}>{assignButton}</span>;
			case 'CREATING':
				return <ActiveTransitionLoadingText taskState={taskState} />;
		}
	}

	return (
		<header
			className={cn(
				layoutStyles.header,
				featureFlags.dsTasklistUI && layoutStyles.headerBorder,
				featureFlags.dsTasklistUI && layoutStyles.headerResponsiveDS,
			)}
			title={t('tasklist.taskDetailsHeader')}
		>
			{featureFlags.dsTasklistUI && isBelowMd ? (
				<Button
					variant="ghost"
					size="sm"
					className={styles.backButtonDS}
					onClick={() => navigate({to: '/tasklist'})}
				>
					<ArrowLeft aria-hidden />
					{t('tasklist.taskDetailsBackToListLabel')}
				</Button>
			) : null}
			<div className={layoutStyles.headerLeftContainer}>
				<span className={cn(styles.taskName, featureFlags.dsTasklistUI && styles.taskNameDS)}>{taskName}</span>
				<span className={cn(styles.processName, featureFlags.dsTasklistUI && styles.processNameDS)}>
					{processName}
				</span>
			</div>
			<div
				className={cn(
					layoutStyles.headerRightContainer,
					featureFlags.dsTasklistUI && layoutStyles.headerRightContainerResponsiveDS,
				)}
			>
				{renderRightContent()}
			</div>
		</header>
	);
};

export {TaskDetailsHeader};
