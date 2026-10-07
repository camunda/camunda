/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {Button, MenuButton, MenuItem} from '@carbon/react';
import {Error, MigrateAlt, Pause, Play, RetryFailed} from '@carbon/react/icons';
import {useMachine} from '@xstate/react';
import {useQueryClient} from '@tanstack/react-query';
import {useNavigate} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {CancelConfirmationModal} from '#/operate/shared/Operations/CancelConfirmationModal';
import {DeleteConfirmationModal} from '#/operate/shared/Operations/DeleteConfirmationModal';
import {isWidthBelowBreakpoint, useMatchMedia} from '#/operate/shared/useMatchMedia';
import {MigrationHelperModal} from '#/operate/pages/Processes/MigrationHelperModal';
import {isMigrationHelperHidden} from '#/operate/pages/Processes/migrationHelperPreference';
import {getInstanceMigrationLocation} from '#/operate/pages/Processes/instanceMigration';
import {ENABLE_PROCESS_MIGRATION} from '#/shared/feature-flags';
import {useProcessInstancePage} from '../useProcessInstancePage';
import {processInstanceHeaderOperationMachine, type HeaderAction} from './processInstanceHeaderOperationMachine';
import {FitContentInlineLoading} from './styled';

function useHeaderOperation(action: HeaderAction, processInstanceKey: string) {
	const queryClient = useQueryClient();
	const navigate = useNavigate();
	const [snapshot, send] = useMachine(processInstanceHeaderOperationMachine, {
		input: {
			action,
			processInstanceKey,
			queryClient,
			onDeleted: () => {
				void navigate({
					to: '/operate/processes',
					search: {active: true, incidents: true, suspended: true, completed: false, canceled: false},
					replace: true,
				});
			},
		},
	});
	return useMemo(
		() => ({action, status: snapshot.value, execute: () => send({type: 'execute'})}),
		[action, snapshot.value, send],
	);
}

type Props = {isModificationModeEnabled?: boolean};

function ProcessInstanceOperations({isModificationModeEnabled = false}: Props) {
	const {processInstance} = useProcessInstancePage();
	const {processInstanceKey, state, hasIncident} = processInstance;
	const {t} = useTranslation();
	const navigate = useNavigate();
	const isCollapsed = useMatchMedia(isWidthBelowBreakpoint('xlg'));
	const [confirmation, setConfirmation] = useState<'cancel' | 'delete' | null>(null);
	const [isMigrationHelperOpen, setIsMigrationHelperOpen] = useState(false);
	const [menuTarget, setMenuTarget] = useState<HTMLDivElement | null>(null);
	const retry = useHeaderOperation('retry', processInstanceKey);
	const cancel = useHeaderOperation('cancel', processInstanceKey);
	const deletion = useHeaderOperation('delete', processInstanceKey);
	const suspend = useHeaderOperation('suspend', processInstanceKey);
	const resume = useHeaderOperation('resume', processInstanceKey);

	const isRunning = state === 'ACTIVE' || state === 'SUSPENDED';
	const canMigrate = ENABLE_PROCESS_MIGRATION && state === 'ACTIVE';
	const operations = useMemo(
		() =>
			state === 'ACTIVE'
				? [...(hasIncident ? [retry] : []), suspend, cancel]
				: state === 'SUSPENDED'
					? [resume, cancel]
					: [deletion],
		[state, hasIncident, retry, suspend, cancel, resume, deletion],
	);
	const controls = useMemo(() => {
		const labels = {
			retry: t('operate.processes.toolbar.retry'),
			cancel: t('operate.processes.toolbar.cancel'),
			delete: t('operate.processes.toolbar.delete'),
			suspend: t('operate.processes.toolbar.suspend'),
			resume: t('operate.processes.toolbar.resume'),
		};
		const pendingLabels = {
			retry: t('operate.processInstance.operations.retrying'),
			cancel: t('operate.processInstance.operations.canceling'),
			delete: t('operate.processInstance.operations.deleting'),
			suspend: t('operate.processInstance.operations.suspending'),
			resume: t('operate.processInstance.operations.resuming'),
		};
		const titles = {
			retry: t('operate.shared.operations.retryTitle', {processInstanceKey}),
			cancel: t('operate.shared.operations.cancelTitle', {processInstanceKey}),
			delete: t('operate.shared.operations.deleteTitle', {processInstanceKey}),
			suspend: t('operate.shared.operations.suspendTitle', {processInstanceKey}),
			resume: t('operate.shared.operations.resumeTitle', {processInstanceKey}),
		};
		const icons = {retry: RetryFailed, cancel: Error, delete: undefined, suspend: Pause, resume: Play};
		return operations.map(({action, status, execute}) => {
			const onClick = () => {
				if (action === 'cancel' || action === 'delete') {
					setConfirmation(action);
				} else {
					execute();
				}
			};
			if (isCollapsed && isRunning) {
				return (
					<MenuItem
						key={action}
						label={labels[action]}
						renderIcon={icons[action]}
						onClick={onClick}
						disabled={status === 'pending'}
					/>
				);
			}
			if (status !== 'idle') {
				return (
					<FitContentInlineLoading
						key={action}
						status={status === 'pending' ? 'active' : status === 'success' ? 'finished' : 'error'}
						description={
							status === 'pending'
								? pendingLabels[action]
								: status === 'success'
									? t('operate.processInstance.operations.successful')
									: t('operate.processInstance.operations.failed')
						}
					/>
				);
			}
			return (
				<Button
					key={action}
					kind={action === 'delete' ? 'danger--ghost' : 'ghost'}
					renderIcon={icons[action]}
					title={titles[action]}
					aria-label={titles[action]}
					size="sm"
					onClick={onClick}
				>
					{labels[action]}
				</Button>
			);
		});
	}, [operations, isCollapsed, isRunning, processInstanceKey, t]);

	const enterMigration = () => void navigate(getInstanceMigrationLocation(processInstance));
	const openMigration = () => {
		if (isMigrationHelperHidden()) {
			enterMigration();
		} else {
			setIsMigrationHelperOpen(true);
		}
	};
	const migrateLabel = t('operate.processes.migration.migrate');
	const migrateTitle = t('operate.shared.operations.migrateTitle', {processInstanceKey});
	const migrateControl = !canMigrate ? null : isCollapsed ? (
		<MenuItem label={migrateLabel} renderIcon={MigrateAlt} onClick={openMigration} />
	) : (
		<Button
			kind="ghost"
			renderIcon={MigrateAlt}
			title={migrateTitle}
			aria-label={migrateTitle}
			size="sm"
			onClick={openMigration}
		>
			{migrateLabel}
		</Button>
	);

	if (isModificationModeEnabled) {
		return null;
	}

	return (
		<>
			{isCollapsed && isRunning ? (
				<MenuButton
					ref={setMenuTarget}
					menuTarget={menuTarget ?? undefined}
					size="sm"
					kind="ghost"
					label={t('operate.processInstance.operations.actions')}
					menuAlignment="bottom-end"
				>
					{controls}
					{migrateControl}
				</MenuButton>
			) : (
				<>
					{controls}
					{migrateControl}
				</>
			)}
			{isMigrationHelperOpen && canMigrate && (
				<MigrationHelperModal
					open
					onClose={() => setIsMigrationHelperOpen(false)}
					onSubmit={() => {
						setIsMigrationHelperOpen(false);
						enterMigration();
					}}
				/>
			)}
			{confirmation === 'cancel' && isRunning && (
				<CancelConfirmationModal
					processInstanceKey={processInstanceKey}
					open
					onCancel={() => setConfirmation(null)}
					onConfirm={() => {
						setConfirmation(null);
						cancel.execute();
					}}
				/>
			)}
			{confirmation === 'delete' && !isRunning && (
				<DeleteConfirmationModal
					processInstanceKey={processInstanceKey}
					open
					onCancel={() => setConfirmation(null)}
					onConfirm={() => {
						setConfirmation(null);
						deletion.execute();
					}}
				/>
			)}
		</>
	);
}

export {ProcessInstanceOperations};
