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
import {observer} from 'mobx-react-lite';
import {Checkbox, Modal, Stack, TableBatchAction} from '@carbon/react';
import {Move} from '@carbon/react/icons';
import {themeStore} from '#/shared/theme/theme';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import modalDiagramImageLight from '#/operate/assets/modal-diagram-image-light.png';
import modalDiagramImageDark from '#/operate/assets/modal-diagram-image-dark.png';
import type {ProcessInstancesSelection} from './useProcessInstancesSelection';
import type {ProcessDefinitionSelection} from './DiagramPanel';
import type {ProcessesMode} from './ProcessesLayout';
import {useDiagramXml} from './useDiagramXml';
import {getMoveSourceRestriction, isAttachedToEventBasedGateway} from './batchModificationTargets';
import {getLegacySharedStateFlag} from '#/operate/shared/utils/getLegacySharedStateFlag';

const HELPER_STORAGE_KEY = 'operate.hideMoveModificationHelperModal';

type Props = {
	mode: ProcessesMode;
	isSubmitting: boolean;
	selection: ProcessInstancesSelection;
	processDefinitionSelection: ProcessDefinitionSelection;
	sourceElementId?: string;
	onEnter: () => void;
};

const MoveAction: React.FC<Props> = observer(
	({mode, isSubmitting, selection, processDefinitionSelection, sourceElementId, onEnter}) => {
		const {t} = useTranslation();
		const [isHelperOpen, setIsHelperOpen] = useState(false);
		const definitionKey =
			processDefinitionSelection.kind === 'single-version'
				? processDefinitionSelection.definition.processDefinitionKey
				: undefined;
		const {data} = useDiagramXml(definitionKey);
		const source = sourceElementId === undefined ? undefined : data?.businessObjects[sourceElementId];
		const restriction = getMoveSourceRestriction(source);
		const disabledReason =
			mode !== 'list'
				? t('operate.processes.toolbar.actionMode')
				: restriction === 'selectElement'
					? t('operate.processes.batchModification.selectElement')
					: restriction === 'unsupportedType'
						? t('operate.processes.batchModification.unsupportedType')
						: !selection.eligibility.cancel
							? t('operate.processes.batchModification.runningOnly')
							: restriction === 'insideMultiInstance'
								? t('operate.processes.batchModification.insideMultiInstance')
								: source !== undefined && isAttachedToEventBasedGateway(source)
									? t('operate.processes.batchModification.eventGateway')
									: undefined;

		return (
			<>
				<TableBatchAction
					renderIcon={Move}
					disabled={isSubmitting || disabledReason !== undefined}
					title={disabledReason}
					onClick={() => {
						if (getStateLocally(HELPER_STORAGE_KEY) ?? getLegacySharedStateFlag('hideMoveModificationHelperModal')) {
							onEnter();
						} else {
							setIsHelperOpen(true);
						}
					}}
				>
					{t('operate.processes.batchModification.move')}
				</TableBatchAction>
				{createPortal(
					<Modal
						open={isHelperOpen}
						preventCloseOnClickOutside
						modalHeading={t('operate.processes.batchModification.helperTitle')}
						primaryButtonText={t('operate.processes.batchModification.continue')}
						secondaryButtonText={t('operate.processes.toolbar.cancel')}
						onRequestClose={() => setIsHelperOpen(false)}
						onSecondarySubmit={() => setIsHelperOpen(false)}
						onRequestSubmit={() => {
							setIsHelperOpen(false);
							onEnter();
						}}
						size="md"
					>
						<Stack gap={5}>
							<p>{t('operate.processes.batchModification.helperDescription')}</p>
							<p>{t('operate.processes.batchModification.helperSelectTarget')}</p>
							<img
								src={themeStore.actualTheme === 'light' ? modalDiagramImageLight : modalDiagramImageDark}
								alt={t('operate.processes.batchModification.helperImageAlt')}
							/>
							<p>{t('operate.processes.batchModification.helperReview')}</p>
							<Checkbox
								id="hide-move-modification-helper"
								labelText={t('operate.processes.batchModification.hideHelper')}
								onChange={(_, {checked}) => storeStateLocally(HELPER_STORAGE_KEY, checked)}
							/>
						</Stack>
					</Modal>,
					document.getElementById('main-content') ?? document.body,
				)}
			</>
		);
	},
);

export {MoveAction};
