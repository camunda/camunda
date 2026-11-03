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
} from '@camunda/design-system';
import {useProcessInstanceModification} from '../useProcessInstanceModification';
import {useProcessInstancePage} from '../useProcessInstancePage';
import {LastModification} from './LastModification';
import {ModificationSummaryModal} from './ModificationSummaryModal';

const ProcessInstanceModificationFooter: React.FC = () => {
	const {t} = useTranslation();
	const {processInstanceId} = useProcessInstancePage();
	const {state, discardModifications} = useProcessInstanceModification();
	const [isDiscardOpen, setIsDiscardOpen] = useState(false);
	const [isSummaryOpen, setIsSummaryOpen] = useState(false);

	return (
		<footer className="flex shrink-0 items-center justify-between gap-4 border-t border-border bg-background px-4 py-2">
			<LastModification />
			<div className="ml-auto flex items-center gap-2">
				<Button variant="secondary" size="sm" onClick={() => setIsDiscardOpen(true)}>
					{t('operate.processInstance.modification.discardAll')}
				</Button>
				<Button size="sm" disabled={state.modifications.length === 0} onClick={() => setIsSummaryOpen(true)}>
					{t('operate.processInstance.modification.review')}
				</Button>
			</div>
			<AlertDialog open={isDiscardOpen} onOpenChange={setIsDiscardOpen}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>{t('operate.processInstance.modification.discardTitle')}</AlertDialogTitle>
						<AlertDialogDescription>
							{t('operate.processInstance.modification.discardDescription', {processInstanceKey: processInstanceId})}{' '}
							{t('operate.processInstance.modification.discardHint')}
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel>{t('operate.processes.toolbar.cancel')}</AlertDialogCancel>
						<AlertDialogAction onClick={discardModifications}>
							{t('operate.processInstance.modification.discard')}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
			{isSummaryOpen && <ModificationSummaryModal onClose={() => setIsSummaryOpen(false)} />}
		</footer>
	);
};

export {ProcessInstanceModificationFooter};
