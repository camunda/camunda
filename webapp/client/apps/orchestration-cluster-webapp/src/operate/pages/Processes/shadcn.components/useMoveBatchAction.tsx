/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Move} from '@camunda/design-system/icons';
import type {ProcessInstancesSelection} from '../useProcessInstancesSelection';
import {useDiagramXml} from '../useDiagramXml';
import {getMoveSourceRestriction, isAttachedToEventBasedGateway} from '../batchModificationTargets';
import type {ProcessDefinitionSelection} from './processDefinitionSelection';
import type {ProcessesMode} from './processesMode';
import {isMoveHelperHidden} from './isMoveHelperHidden';

type Props = {
	mode: ProcessesMode;
	isSubmitting: boolean;
	selection: ProcessInstancesSelection;
	processDefinitionSelection?: ProcessDefinitionSelection;
	sourceElementId?: string;
	onEnter: () => void;
};

function useMoveBatchAction({
	mode,
	isSubmitting,
	selection,
	processDefinitionSelection,
	sourceElementId,
	onEnter,
}: Props) {
	const {t} = useTranslation();
	const [isHelperOpen, setIsHelperOpen] = useState(false);
	const definitionKey =
		processDefinitionSelection?.kind === 'single-version'
			? processDefinitionSelection.definition.processDefinitionKey
			: undefined;
	const {data} = useDiagramXml(definitionKey);
	const source = sourceElementId === undefined ? undefined : data?.businessObjects[sourceElementId];
	const restriction = getMoveSourceRestriction(source);
	const disabledReason =
		mode !== 'list'
			? t('operate.processes.toolbar.actionModeDs')
			: restriction === 'selectElement'
				? t('operate.processes.batchModification.selectElement')
				: restriction === 'unsupportedType'
					? t('operate.processes.batchModification.unsupportedType')
					: !selection.eligibility.running
						? t('operate.processes.batchModification.runningOnly')
						: restriction === 'insideMultiInstance'
							? t('operate.processes.batchModification.insideMultiInstance')
							: source !== undefined && isAttachedToEventBasedGateway(source)
								? t('operate.processes.batchModification.eventGateway')
								: undefined;

	return {
		action: {
			id: 'move',
			label: t('operate.processes.batchModification.move'),
			icon: <Move aria-hidden />,
			disabled: isSubmitting || disabledReason !== undefined,
			onClick: () => {
				if (isMoveHelperHidden()) {
					onEnter();
				} else {
					setIsHelperOpen(true);
				}
			},
		},
		disabledReason,
		helperProps: {
			open: isHelperOpen,
			onClose: () => setIsHelperOpen(false),
			onSubmit: () => {
				setIsHelperOpen(false);
				onEnter();
			},
		},
	};
}

type MoveBatchAction = ReturnType<typeof useMoveBatchAction>;

export {useMoveBatchAction};
export type {MoveBatchAction};
