/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OPERATE_BASE} from '#/shared/operateBase';
import {useMemo, useState} from 'react';
import {Button, DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger} from '@camunda/design-system';
import {
	Ban,
	CircleAlert,
	CircleCheck,
	CirclePause,
	CirclePlay,
	LoaderCircle,
	RotateCcw,
	Trash2,
	Wrench,
} from '@camunda/design-system/icons';
import {useMachine} from '@xstate/react';
import {useQueryClient} from '@tanstack/react-query';
import {useNavigate} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {CancelConfirmationModal} from '#/operate/shared/Operations/shadcn.components/CancelConfirmationModal';
import {DeleteConfirmationModal} from '#/operate/shared/Operations/shadcn.components/DeleteConfirmationModal';
import {ENABLE_PROCESS_INSTANCE_MODIFICATION} from '#/shared/feature-flags';
import {useMatchMedia} from '#/operate/shared/useMatchMedia';
import {useProcessInstancePage} from '#/operate/pages/ProcessInstance/useProcessInstancePage';
import {useProcessInstanceModification} from '#/operate/pages/ProcessInstance/useProcessInstanceModification';
import {isModificationHelperHidden} from '#/operate/pages/ProcessInstance/modificationHelperPreference';
import {ModificationHelperModal} from '#/operate/pages/ProcessInstance/shadcn.components/ModificationHelperModal';
import {COLLAPSED_LAYOUT_QUERY} from '../../shadcn.components/collapsedLayoutQuery';
import {processInstanceHeaderOperationMachine, type HeaderAction} from '../processInstanceHeaderOperationMachine';

const getRootInstancePath = (rootInstanceId: string) => `${OPERATE_BASE}/processes/${rootInstanceId}`;

const ICONS = {retry: RotateCcw, cancel: Ban, delete: Trash2, suspend: CirclePause, resume: CirclePlay};

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
					to: `${OPERATE_BASE}/processes` as const,
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

const ProcessInstanceOperations: React.FC = () => {
	const {processInstance} = useProcessInstancePage();
	const {processInstanceKey, state, hasIncident} = processInstance;
	const {t} = useTranslation();
	const {isModificationModeEnabled, enterModificationMode} = useProcessInstanceModification();
	const [isModificationHelperOpen, setIsModificationHelperOpen] = useState(false);
	const isCollapsed = useMatchMedia(COLLAPSED_LAYOUT_QUERY);
	const [confirmation, setConfirmation] = useState<'cancel' | 'delete' | null>(null);
	const retry = useHeaderOperation('retry', processInstanceKey);
	const cancel = useHeaderOperation('cancel', processInstanceKey);
	const deletion = useHeaderOperation('delete', processInstanceKey);
	const suspend = useHeaderOperation('suspend', processInstanceKey);
	const resume = useHeaderOperation('resume', processInstanceKey);

	const isRunning = state === 'ACTIVE' || state === 'SUSPENDED';
	const operations = useMemo(
		() =>
			state === 'ACTIVE'
				? [...(hasIncident ? [retry] : []), suspend, cancel]
				: state === 'SUSPENDED'
					? [resume, cancel]
					: [deletion],
		[state, hasIncident, retry, suspend, cancel, resume, deletion],
	);

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

	const execute = (action: HeaderAction, run: () => void) => {
		if (action === 'cancel' || action === 'delete') {
			setConfirmation(action);
		} else {
			run();
		}
	};

	const renderStatusContent = ({action, status}: (typeof operations)[number]) => {
		const Icon = status === 'pending' ? LoaderCircle : status === 'success' ? CircleCheck : CircleAlert;
		return (
			<>
				<Icon aria-hidden="true" className={status === 'pending' ? 'size-4 animate-spin' : 'size-4'} />
				{status === 'pending'
					? pendingLabels[action]
					: status === 'success'
						? t('operate.processInstance.operations.successful')
						: t('operate.processInstance.operations.failed')}
			</>
		);
	};

	const activeOperation =
		operations.find(({status}) => status === 'pending') ?? operations.find(({status}) => status !== 'idle');

	const openModification = () => {
		if (isModificationHelperHidden()) {
			enterModificationMode();
		} else {
			setIsModificationHelperOpen(true);
		}
	};
	const modifyLabel = t('operate.processInstance.modification.modify');
	const modifyTitle = t('operate.shared.operations.modifyTitle', {processInstanceKey});
	const canModify = ENABLE_PROCESS_INSTANCE_MODIFICATION && state === 'ACTIVE';

	if (isModificationModeEnabled) {
		return null;
	}

	return (
		<>
			{isCollapsed && isRunning ? (
				<>
					<DropdownMenu>
						<DropdownMenuTrigger asChild>
							<Button variant="ghost" size="sm">
								{t('operate.processInstance.operations.actions')}
							</Button>
						</DropdownMenuTrigger>
						<DropdownMenuContent align="end">
							{operations.map(({action, status, execute: run}) => {
								const Icon = ICONS[action];
								return (
									<DropdownMenuItem key={action} disabled={status === 'pending'} onSelect={() => execute(action, run)}>
										{Icon !== undefined && <Icon aria-hidden="true" />}
										{labels[action]}
									</DropdownMenuItem>
								);
							})}
							{canModify && (
								<DropdownMenuItem onSelect={openModification}>
									<Wrench aria-hidden="true" />
									{modifyLabel}
								</DropdownMenuItem>
							)}
						</DropdownMenuContent>
					</DropdownMenu>
					<span role="status" aria-live="polite" className="flex items-center gap-2 text-sm whitespace-nowrap">
						{activeOperation !== undefined && renderStatusContent(activeOperation)}
					</span>
				</>
			) : (
				operations.map((operation) => {
					const {action, status, execute: run} = operation;
					if (status !== 'idle') {
						return (
							<span
								key={action}
								role="status"
								aria-live="polite"
								className="flex items-center gap-2 text-sm whitespace-nowrap"
							>
								{renderStatusContent(operation)}
							</span>
						);
					}
					const Icon = ICONS[action];
					return (
						<Button
							key={action}
							variant={action === 'delete' ? 'destructive' : 'ghost'}
							size="sm"
							title={titles[action]}
							aria-label={titles[action]}
							onClick={() => execute(action, run)}
						>
							{action === 'delete' ? (
								<>
									<Icon aria-hidden="true" />
									<span className="-ml-1.5 grid grid-cols-[0fr] transition-[grid-template-columns,margin] duration-150 ease-out lg:ml-0 lg:grid-cols-[1fr]">
										<span className="overflow-hidden whitespace-nowrap">{labels[action]}</span>
									</span>
								</>
							) : (
								<>
									{Icon !== undefined && <Icon aria-hidden="true" data-icon="inline-start" />}
									{labels[action]}
								</>
							)}
						</Button>
					);
				})
			)}
			{!(isCollapsed && isRunning) && canModify && (
				<Button variant="ghost" size="sm" title={modifyTitle} aria-label={modifyTitle} onClick={openModification}>
					<Wrench aria-hidden="true" data-icon="inline-start" />
					{modifyLabel}
				</Button>
			)}
			{isModificationHelperOpen && state === 'ACTIVE' && (
				<ModificationHelperModal
					open
					onClose={() => setIsModificationHelperOpen(false)}
					onSubmit={() => {
						setIsModificationHelperOpen(false);
						enterModificationMode();
					}}
				/>
			)}
			{confirmation === 'cancel' && isRunning && (
				<CancelConfirmationModal
					processInstanceKey={processInstanceKey}
					open
					getRootInstancePath={getRootInstancePath}
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
};

export {ProcessInstanceOperations};
