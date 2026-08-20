/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Stack} from '@carbon/react';
import {CheckmarkFilled} from '@carbon/react/icons';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {AssigneeTag} from '#/tasklist/modules/available-tasks/components/AssigneeTag';
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

	function renderRightContent() {
		switch (taskState) {
			case 'COMPLETED':
				return (
					<span
						className={styles.taskStatus}
						data-testid="completion-label"
						title={t('tasklist.taskDetailsTaskCompletedBy')}
					>
						<Stack className={styles.alignItemsCenter} orientation="horizontal" gap={2}>
							<CheckmarkFilled size={16} color="green" />
							{assignee ? (
								<>
									{t('tasklist.taskDetailsTaskCompletedBy') + ' '}
									<span className={styles.taskAssignee} data-testid="assignee">
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
						<span className={styles.taskAssignee} data-testid="assignee">
							<AssigneeTag currentUser={user} assignee={assignee} isShortFormat={false} />
						</span>
						<span className={styles.assignButtonContainer}>{assignButton}</span>
					</>
				);
			case 'UPDATING':
			case 'CANCELING':
				return (
					<>
						<ActiveTransitionLoadingText taskState={taskState} />
						<span className={styles.taskAssignee} data-testid="assignee">
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
		<header className={layoutStyles.header} title={t('tasklist.taskDetailsHeader')}>
			<div className={layoutStyles.headerLeftContainer}>
				<span className={styles.taskName}>{taskName}</span>
				<span className={styles.processName}>{processName}</span>
			</div>
			<div className={layoutStyles.headerRightContainer}>{renderRightContent()}</div>
		</header>
	);
};

export {TaskDetailsHeader};
