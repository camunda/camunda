/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense} from 'react';
import {useTranslation} from 'react-i18next';
import {ErrorBoundary} from 'react-error-boundary';
import {QueryErrorResetBoundary} from '@tanstack/react-query';
import {Button} from '@carbon/react';
import type {DecisionDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {DecisionViewer} from '#/operate/shared/DecisionViewer';
import {DiagramShell} from '#/operate/shared/DiagramShell/DiagramShell';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {DecisionHeader} from './DecisionHeader';
import {useDecisionDefinitionXml} from './useDecisionDefinitionXml';
import {getDecisionDefinitionName} from './getDecisionDefinitionName';
import {DecisionError, Section} from './styled';

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

const DecisionDiagram: React.FC<{definition: DecisionDefinition}> = ({definition}) => {
	const {data: xml} = useDecisionDefinitionXml(definition.decisionDefinitionKey);

	return (
		<DiagramShell status="content">
			<DecisionViewer xml={xml} decisionViewId={definition.decisionDefinitionId} />
		</DiagramShell>
	);
};

const DecisionDiagramError: React.FC<{onRetry: () => void}> = ({onRetry}) => {
	const {t} = useTranslation();

	return (
		<DecisionError gap={5}>
			<ErrorMessage />
			<Button kind="tertiary" size="sm" onClick={onRetry}>
				{t('errorGenericErrorPageButtonLabel')}
			</Button>
		</DecisionError>
	);
};

const DecisionPanel: React.FC<Props> = ({
	decisionDefinitionSelection,
	isDefinitionSelectionLoading = false,
	isDefinitionSelectionError = false,
}) => {
	const {t} = useTranslation();
	const selectedDefinitionName =
		decisionDefinitionSelection.kind !== 'no-match'
			? getDecisionDefinitionName(decisionDefinitionSelection.definition)
			: t('operate.decisions.diagramHeader.title');

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
			{(() => {
				if (isDefinitionSelectionLoading) {
					return <DiagramShell status="loading">{null}</DiagramShell>;
				}
				if (isDefinitionSelectionError) {
					return <DiagramShell status="error">{null}</DiagramShell>;
				}
				if (decisionDefinitionSelection.kind !== 'single-version') {
					return (
						<DiagramShell status="empty" emptyMessage={getEmptyMessage()}>
							{null}
						</DiagramShell>
					);
				}

				return (
					<QueryErrorResetBoundary>
						{({reset}) => (
							<ErrorBoundary
								onReset={reset}
								fallbackRender={({resetErrorBoundary}) => <DecisionDiagramError onRetry={resetErrorBoundary} />}
								resetKeys={[decisionDefinitionSelection.definition.decisionDefinitionKey]}
							>
								<Suspense fallback={<DiagramShell status="loading">{null}</DiagramShell>}>
									<DecisionDiagram definition={decisionDefinitionSelection.definition} />
								</Suspense>
							</ErrorBoundary>
						)}
					</QueryErrorResetBoundary>
				);
			})()}
		</Section>
	);
};

export {DecisionPanel};
export type {DecisionDefinitionSelection};
