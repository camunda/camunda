/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {createPortal} from 'react-dom';
import {useTranslation} from 'react-i18next';
import {Button, Modal, Table, TableBody, TableCell, TableHead, TableRow} from '@carbon/react';
import type {ProcessDefinitionSelection} from './DiagramPanel';
import type {ProcessesNavigationBlocker} from './ProcessesLayout';
import {getActiveModificationFilter} from './getActiveModificationFilter';
import {getProcessDefinitionName} from './getProcessDefinitionName';
import {BatchModificationActions, SummaryTableHeader, SummaryTitle} from './styled';
import {useBatchModificationStatistics, type BatchModificationScope} from './useBatchModificationStatistics';

type Props = {
	blocker: ProcessesNavigationBlocker;
	scope: BatchModificationScope;
	processDefinitionSelection: ProcessDefinitionSelection;
	isDefinitionChanged: boolean;
	sourceElementId?: string;
	targetElementId?: string;
	sourceLabel: string;
	targetLabel: string;
	onExit: () => void;
	onSubmit: (move: {sourceElementId: string; targetElementId: string}) => void;
};

function BatchModificationFooter({
	blocker,
	scope,
	processDefinitionSelection,
	isDefinitionChanged,
	sourceElementId,
	targetElementId,
	sourceLabel,
	targetLabel,
	onExit,
	onSubmit,
}: Props) {
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
	const isScopeReviewable =
		scope.selectedCount >= 1 && getActiveModificationFilter(scope.filter) !== null && !isDefinitionChanged;
	const isExitConfirmationOpen = isExitOpen || blocker.status === 'blocked';

	return (
		<>
			<BatchModificationActions orientation="horizontal" gap={5}>
				<Button kind="secondary" size="sm" onClick={() => setIsExitOpen(true)}>
					{t('operate.processes.batchModification.exit')}
				</Button>
				<Button
					size="sm"
					disabled={!isScopeReviewable || !isTargetElementSelected}
					onClick={() => setIsReviewOpen(true)}
				>
					{t('operate.processes.batchModification.review')}
				</Button>
			</BatchModificationActions>
			{createPortal(
				<Modal
					open={isExitConfirmationOpen}
					modalHeading={t('operate.processes.batchModification.exitTitle')}
					preventCloseOnClickOutside
					primaryButtonText={t('operate.processes.batchModification.exit')}
					secondaryButtonText={t('operate.processes.toolbar.cancel')}
					danger={isTargetElementSelected}
					onRequestClose={() => {
						blocker.reset?.();
						setIsExitOpen(false);
					}}
					onRequestSubmit={() => {
						setIsExitOpen(false);
						onExit();
					}}
				>
					{isTargetElementSelected && <p>{t('operate.processes.batchModification.discard')}</p>}
					<p>{t('operate.processes.batchModification.exitProceed')}</p>
				</Modal>,
				document.getElementById('main-content') ?? document.body,
			)}
			{createPortal(
				<Modal
					open={isReviewOpen && !isExitConfirmationOpen}
					modalHeading={t('operate.processes.batchModification.summaryTitle')}
					preventCloseOnClickOutside
					size="lg"
					primaryButtonText={t('operate.processes.toolbar.apply')}
					primaryButtonDisabled={!isScopeReviewable || sourceElementId === undefined || !isTargetElementSelected}
					secondaryButtonText={t('operate.processes.toolbar.cancel')}
					onRequestClose={() => setIsReviewOpen(false)}
					onRequestSubmit={() => {
						if (!isScopeReviewable || sourceElementId === undefined || targetElementId === undefined) {
							return;
						}
						setIsReviewOpen(false);
						onSubmit({sourceElementId, targetElementId});
					}}
				>
					<p>{t('operate.processes.batchModification.summaryDescription', {processName})}</p>
					<SummaryTitle>{t('operate.processes.batchModification.summaryHeading')}</SummaryTitle>
					<Table>
						<TableHead>
							<TableRow>
								<SummaryTableHeader $width="30%">{t('operate.batchOperations.operation')}</SummaryTableHeader>
								<SummaryTableHeader $width="40%">{t('operate.processes.filters.element')}</SummaryTableHeader>
								<SummaryTableHeader $width="30%">
									{t('operate.processes.batchModification.affectedInstances')}
								</SummaryTableHeader>
							</TableRow>
						</TableHead>
						<TableBody>
							<TableRow>
								<TableCell>{t('operate.processes.batchModification.batchMove')}</TableCell>
								<TableCell>{`${sourceLabel} --> ${targetLabel}`}</TableCell>
								<TableCell>{count}</TableCell>
							</TableRow>
						</TableBody>
					</Table>
				</Modal>,
				document.getElementById('main-content') ?? document.body,
			)}
		</>
	);
}

export {BatchModificationFooter};
