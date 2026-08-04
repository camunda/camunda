/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import type {DecisionDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {DecisionViewer} from '#/operate/shared/DecisionViewer';
import {DiagramShell} from '#/operate/shared/DiagramShell/DiagramShell';
import {DecisionHeader} from './DecisionHeader';
import {useDecisionDefinitionXml} from './useDecisionDefinitionXml';
import {getDecisionDefinitionName} from './getDecisionDefinitionName';
import {Section} from './styled';

type DecisionDefinitionSelection =
	| {kind: 'no-match'}
	| {kind: 'single-version'; definition: DecisionDefinition}
	| {kind: 'all-versions'; definition: Pick<DecisionDefinition, 'name' | 'decisionDefinitionId'>}
	| {kind: 'multiple-tenants'; definition: Pick<DecisionDefinition, 'name' | 'decisionDefinitionId'>};

type Props = {
	decisionDefinitionSelection: DecisionDefinitionSelection;
	isDefinitionSelectionLoading?: boolean;
	isDefinitionSelectionError?: boolean;
};

const DecisionPanel: React.FC<Props> = ({
	decisionDefinitionSelection,
	isDefinitionSelectionLoading = false,
	isDefinitionSelectionError = false,
}) => {
	const {t} = useTranslation();
	const selectedDefinitionKey =
		decisionDefinitionSelection.kind === 'single-version'
			? decisionDefinitionSelection.definition.decisionDefinitionKey
			: undefined;
	const selectedDefinitionId =
		decisionDefinitionSelection.kind === 'single-version'
			? decisionDefinitionSelection.definition.decisionDefinitionId
			: undefined;
	const selectedDefinitionName =
		decisionDefinitionSelection.kind !== 'no-match'
			? getDecisionDefinitionName(decisionDefinitionSelection.definition)
			: t('operate.decisions.diagramHeader.title');

	const {data: xml, isFetching: isXmlFetching, isError: isXmlError} = useDecisionDefinitionXml(selectedDefinitionKey);

	const getStatus = () => {
		if (isDefinitionSelectionLoading || isXmlFetching) {
			return 'loading';
		}
		if (isDefinitionSelectionError || isXmlError) {
			return 'error';
		}
		if (decisionDefinitionSelection.kind !== 'single-version') {
			return 'empty';
		}
		return 'content';
	};

	const getEmptyMessage = () => {
		switch (decisionDefinitionSelection.kind) {
			case 'all-versions':
				return {
					message: t('operate.decisions.diagramPanel.multipleVersionsSelected', {name: selectedDefinitionName}),
					additionalInfo: t('operate.decisions.diagramPanel.selectSingleVersion'),
				};
			case 'multiple-tenants':
				return {
					message: t('operate.decisions.diagramPanel.multipleTenantsSelected', {name: selectedDefinitionName}),
					additionalInfo: t('operate.decisions.diagramPanel.selectSingleTenant'),
				};
			default:
				return {
					message: t('operate.decisions.diagramPanel.noDecisionSelected'),
					additionalInfo: t('operate.decisions.diagramPanel.selectDecisionInFilters'),
				};
		}
	};

	return (
		<Section aria-label="Decision Panel">
			<DecisionHeader decisionDefinitionSelection={decisionDefinitionSelection} />
			<DiagramShell status={getStatus()} emptyMessage={getEmptyMessage()}>
				<DecisionViewer xml={xml ?? null} decisionViewId={selectedDefinitionId ?? null} />
			</DiagramShell>
		</Section>
	);
};

export {DecisionPanel};
export type {DecisionDefinitionSelection};
