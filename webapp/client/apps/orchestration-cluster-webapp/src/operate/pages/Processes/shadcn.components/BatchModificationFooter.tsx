/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
	Button,
	Dialog,
	DialogBody,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Heading,
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from '@camunda/design-system';
import {getProcessDefinitionName} from '../getProcessDefinitionName';
import {useBatchModificationStatistics, type BatchModificationScope} from '../useBatchModificationStatistics';
import type {ProcessDefinitionSelection} from './processDefinitionSelection';
import type {ProcessesNavigationBlocker} from './processesMode';

type Props = {
	blocker: ProcessesNavigationBlocker;
	scope: BatchModificationScope;
	hasActiveScope: boolean;
	processDefinitionSelection: ProcessDefinitionSelection;
	isDefinitionChanged: boolean;
	sourceElementId?: string;
	targetElementId?: string;
	sourceLabel: string;
	targetLabel: string;
	onExit: () => void;
	onSubmit: (move: {sourceElementId: string; targetElementId: string}) => void;
};

const BatchModificationFooter: React.FC<Props> = ({
	blocker,
	scope,
	hasActiveScope,
	processDefinitionSelection,
	isDefinitionChanged,
	sourceElementId,
	targetElementId,
	sourceLabel,
	targetLabel,
	onExit,
	onSubmit,
}) => {
	const {t} = useTranslation();
	const [isExitOpen, setIsExitOpen] = useState(false);
	const [isReviewOpen, setIsReviewOpen] = useState(false);
	const definitionKey =
		processDefinitionSelection.kind === 'single-version'
			? processDefinitionSelection.definition.processDefinitionKey
			: undefined;
	const processName =
		processDefinitionSelection.kind === 'no-match'
			? t('operate.processes.diagramHeader.title')
			: getProcessDefinitionName(processDefinitionSelection.definition);
	const count = useBatchModificationStatistics({definitionKey, sourceElementId, scope});
	const isTargetElementSelected = targetElementId !== undefined;
	const isScopeReviewable = scope.selectedCount >= 1 && hasActiveScope && !isDefinitionChanged;
	const isExitConfirmationOpen = isExitOpen || blocker.status === 'blocked';
	const canApply = isScopeReviewable && sourceElementId !== undefined && isTargetElementSelected;

	return (
		<footer className="flex shrink-0 items-center justify-end gap-4 border-t border-border bg-background px-4 py-2">
			<Button variant="secondary" size="sm" onClick={() => setIsExitOpen(true)}>
				{t('operate.processes.batchModification.exit')}
			</Button>
			<Button size="sm" disabled={!canApply} onClick={() => setIsReviewOpen(true)}>
				{t('operate.processes.batchModification.reviewDs')}
			</Button>
			<AlertDialog
				open={isExitConfirmationOpen}
				onOpenChange={(nextOpen) => {
					if (!nextOpen) {
						blocker.reset?.();
						setIsExitOpen(false);
					}
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>{t('operate.processes.batchModification.exitTitle')}</AlertDialogTitle>
						<AlertDialogDescription>
							{isTargetElementSelected
								? t('operate.processes.batchModification.exitDescriptionDs')
								: t('operate.processes.batchModification.exitDescriptionEmptyDs')}
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel>{t('operate.processes.toolbar.cancel')}</AlertDialogCancel>
						<AlertDialogAction
							onClick={(event) => {
								event.preventDefault();
								setIsExitOpen(false);
								onExit();
							}}
						>
							{t('operate.processes.batchModification.exit')}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
			<Dialog
				open={isReviewOpen && !isExitConfirmationOpen}
				onOpenChange={(nextOpen) => {
					if (!nextOpen) {
						setIsReviewOpen(false);
					}
				}}
			>
				<DialogContent size="lg" dismissOnOutsideClick={false}>
					<DialogHeader>
						<DialogTitle>{t('operate.processes.batchModification.summaryTitle')}</DialogTitle>
						<DialogDescription>
							{t('operate.processes.batchModification.summaryDescription', {processName})}
						</DialogDescription>
					</DialogHeader>
					<DialogBody className="flex flex-col gap-3">
						<Heading as="h3" variant="heading-xs">
							{t('operate.processes.batchModification.summaryHeading')}
						</Heading>
						<Table>
							<TableHeader>
								<TableRow>
									<TableHead className="w-[30%]">{t('operate.batchOperations.operation')}</TableHead>
									<TableHead className="w-[40%]">{t('operate.processes.filters.element')}</TableHead>
									<TableHead className="w-[30%]">
										{t('operate.processes.batchModification.affectedInstances')}
									</TableHead>
								</TableRow>
							</TableHeader>
							<TableBody>
								<TableRow>
									<TableCell>{t('operate.processes.batchModification.batchMove')}</TableCell>
									<TableCell>{`${sourceLabel} --> ${targetLabel}`}</TableCell>
									<TableCell>{count}</TableCell>
								</TableRow>
							</TableBody>
						</Table>
					</DialogBody>
					<DialogFooter>
						<Button variant="secondary" onClick={() => setIsReviewOpen(false)}>
							{t('operate.processes.toolbar.cancel')}
						</Button>
						<Button
							disabled={!canApply}
							onClick={() => {
								if (sourceElementId === undefined || targetElementId === undefined) {
									return;
								}
								setIsReviewOpen(false);
								onSubmit({sourceElementId, targetElementId});
							}}
						>
							{t('operate.processes.toolbar.apply')}
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</footer>
	);
};

export {BatchModificationFooter};
