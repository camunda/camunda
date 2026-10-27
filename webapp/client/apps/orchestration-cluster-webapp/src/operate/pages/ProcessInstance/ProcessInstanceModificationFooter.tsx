/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Button, Modal} from '@carbon/react';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {useProcessInstancePage} from './useProcessInstancePage';
import {LastModification} from './LastModification';
import {ModificationSummaryModal} from './ModificationSummaryModal';
import {Buttons, ModificationFooter} from './processInstanceModification.styled';

const ProcessInstanceModificationFooter: React.FC = () => {
	const {t} = useTranslation();
	const {processInstanceId} = useProcessInstancePage();
	const {state, discardModifications} = useProcessInstanceModification();
	const [isDiscardOpen, setIsDiscardOpen] = useState(false);
	const [isSummaryOpen, setIsSummaryOpen] = useState(false);

	return (
		<ModificationFooter>
			<LastModification />
			<Buttons orientation="horizontal" gap={4}>
				<Button kind="secondary" size="sm" onClick={() => setIsDiscardOpen(true)}>
					{t('operate.processInstance.modification.discardAll')}
				</Button>
				<Button
					kind="primary"
					size="sm"
					disabled={state.modifications.length === 0}
					onClick={() => setIsSummaryOpen(true)}
				>
					{t('operate.processInstance.modification.review')}
				</Button>
			</Buttons>
			<Modal
				open={isDiscardOpen}
				modalHeading={t('operate.processInstance.modification.discardTitle')}
				preventCloseOnClickOutside
				danger
				primaryButtonText={t('operate.processInstance.modification.discard')}
				secondaryButtonText={t('operate.processes.toolbar.cancel')}
				onRequestClose={() => setIsDiscardOpen(false)}
				onRequestSubmit={() => {
					discardModifications();
					setIsDiscardOpen(false);
				}}
			>
				<p>{t('operate.processInstance.modification.discardDescription', {processInstanceKey: processInstanceId})}</p>
				<p>{t('operate.processInstance.modification.discardHint')}</p>
			</Modal>
			{isSummaryOpen && <ModificationSummaryModal onClose={() => setIsSummaryOpen(false)} />}
		</ModificationFooter>
	);
};

export {ProcessInstanceModificationFooter};
