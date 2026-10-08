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
import {Button} from '@camunda/design-system';
import type {DecisionDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {DecisionViewer} from '#/operate/shared/DecisionViewer/shadcn.components/DecisionViewer';
import {DiagramShell} from '#/operate/shared/DiagramShell/shadcn.components/DiagramShell';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/shadcn.components/ErrorMessage';
import {useDecisionDefinitionXml} from '../useDecisionDefinitionXml';
import {getDecisionDefinitionName} from '../getDecisionDefinitionName';
import {DecisionHeader} from './DecisionHeader';
import type {DecisionDefinitionSelection} from './decisionDefinitionSelection';

type Props = {
	decisionDefinitionSelection: DecisionDefinitionSelection;
	isDefinitionSelectionLoading?: boolean;
	isDefinitionSelectionError?: boolean;
	headerActions?: React.ReactNode;
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
		<div className="flex grow flex-col items-center justify-center gap-4">
			<ErrorMessage />
			<Button type="button" variant="secondary" size="sm" onClick={onRetry}>
				{t('errorGenericErrorPageButtonLabel')}
			</Button>
		</div>
	);
};

const DecisionPanelBody: React.FC<Omit<Props, 'headerActions'>> = ({
	decisionDefinitionSelection,
	isDefinitionSelectionLoading = false,
	isDefinitionSelectionError = false,
}) => {
	const {t} = useTranslation();

	if (isDefinitionSelectionLoading) {
		return <DiagramShell status="loading" />;
	}
	if (isDefinitionSelectionError) {
		return <DiagramShell status="error" />;
	}
	if (decisionDefinitionSelection.kind !== 'single-version') {
		const selectedDefinitionName =
			decisionDefinitionSelection.kind !== 'no-match'
				? getDecisionDefinitionName(decisionDefinitionSelection.definition)
				: t('operate.decisions.diagramHeader.title');

		switch (decisionDefinitionSelection.kind) {
			case 'all-versions':
				return (
					<DiagramShell
						status="empty"
						emptyMessage={{
							message: t('operate.decisions.diagramPanel.multipleVersionsSelected', {name: selectedDefinitionName}),
							additionalInfo: t('operate.decisions.diagramPanel.selectSingleVersion'),
						}}
					/>
				);
			case 'multiple-tenants':
				return (
					<DiagramShell
						status="empty"
						emptyMessage={{
							message: t('operate.decisions.diagramPanel.multipleTenantsSelected', {name: selectedDefinitionName}),
							additionalInfo: t('operate.decisions.diagramPanel.selectSingleTenant'),
						}}
					/>
				);
			default:
				return (
					<DiagramShell
						status="empty"
						emptyMessage={{
							message: t('operate.decisions.diagramPanel.noDecisionSelected'),
							additionalInfo: t('operate.decisions.diagramPanel.selectDecisionInFilters'),
						}}
					/>
				);
		}
	}

	return (
		<QueryErrorResetBoundary>
			{({reset}) => (
				<ErrorBoundary
					onReset={reset}
					fallbackRender={({resetErrorBoundary}) => <DecisionDiagramError onRetry={resetErrorBoundary} />}
					resetKeys={[decisionDefinitionSelection.definition.decisionDefinitionKey]}
				>
					<Suspense fallback={<DiagramShell status="loading" />}>
						<DecisionDiagram definition={decisionDefinitionSelection.definition} />
					</Suspense>
				</ErrorBoundary>
			)}
		</QueryErrorResetBoundary>
	);
};

const DecisionPanel: React.FC<Props> = ({headerActions, ...bodyProps}) => {
	const {t} = useTranslation();

	return (
		<section aria-label={t('operate.decisions.diagramPanel.ariaLabel')} className="flex h-full flex-col">
			<DecisionHeader decisionDefinitionSelection={bodyProps.decisionDefinitionSelection}>
				{headerActions}
			</DecisionHeader>
			<DecisionPanelBody {...bodyProps} />
		</section>
	);
};

export {DecisionPanel};
