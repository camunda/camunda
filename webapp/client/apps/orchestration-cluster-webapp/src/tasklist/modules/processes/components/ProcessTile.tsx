/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {Stack, Tag} from '@carbon/react';
import {ArrowRight, List} from '@carbon/react/icons';
import {t as _t} from 'i18next';
import {useTranslation} from 'react-i18next';
import {AsyncActionButton} from '#/tasklist/modules/task-details/components/AsyncActionButton/AsyncActionButton';
import type {StartProcessStatus} from '#/tasklist/modules/processes/startProcessMachine';
import styles from './ProcessTile.module.scss';
import {useMemo} from 'react';

type Props = {
	process: ProcessDefinition;
	status: StartProcessStatus;
	isStartButtonDisabled: boolean;
	onStartProcess: () => void;
};

function getStartProcessStatusDescription(status: StartProcessStatus): string | undefined {
	if (status === 'active') {
		return _t('tasklist.processesStartProcessPendingStatusText');
	}

	if (status === 'active-tasks') {
		return _t('tasklist.processesStartProcessWaitForTasksText');
	}

	if (status === 'finished') {
		return _t('tasklist.processesStartProcessSuccess');
	}

	if (status === 'error') {
		return _t('tasklist.processesStartProcessFailed');
	}

	return undefined;
}

function getInlineLoadingStatus(status: StartProcessStatus) {
	return status === 'active-tasks' ? 'active' : status;
}

const ProcessTile: React.FC<Props> = ({process, status, isStartButtonDisabled, onStartProcess}) => {
	const {t} = useTranslation();
	const displayName = process.name ?? process.processDefinitionId;
	const statusDescription = getStartProcessStatusDescription(status);
	const buttonProps = useMemo(
		() =>
			({
				type: 'button',
				kind: 'tertiary',
				size: 'sm',
				renderIcon: process.hasStartForm ? ArrowRight : undefined,
				disabled: isStartButtonDisabled,
				onClick: onStartProcess,
			}) as const,
		[process.hasStartForm, isStartButtonDisabled, onStartProcess],
	);
	const inlineLoadingProps = useMemo(
		() =>
			({
				description: statusDescription,
				'aria-live': status === 'error' || status === 'finished' ? 'assertive' : 'polite',
			}) as const,
		[status, statusDescription],
	);

	return (
		<div className={styles.container}>
			<Stack className={styles.content}>
				<Stack className={styles.titleWrapper}>
					<div className={styles.titleRow}>
						<h2 className={styles.title} title={displayName}>
							{displayName}
						</h2>
					</div>
					<span className={styles.subtitle} title={process.processDefinitionId}>
						{displayName === process.processDefinitionId ? '' : process.processDefinitionId}
					</span>
				</Stack>
				<div className={styles.buttonRow}>
					<ul
						className={styles.attributes}
						title={t('tasklist.processesProcessTileAttributes')}
						aria-hidden={!process.hasStartForm}
					>
						{process.hasStartForm ? (
							<li>
								<Tag renderIcon={List}>{t('tasklist.processesProcessTileAttributeRequiresForm')}</Tag>
							</li>
						) : null}
					</ul>
					<AsyncActionButton
						status={getInlineLoadingStatus(status)}
						buttonProps={buttonProps}
						inlineLoadingProps={inlineLoadingProps}
					>
						{t('tasklist.processesTileStartProcessButtonLabel')}
					</AsyncActionButton>
				</div>
			</Stack>
		</div>
	);
};

export {ProcessTile};
