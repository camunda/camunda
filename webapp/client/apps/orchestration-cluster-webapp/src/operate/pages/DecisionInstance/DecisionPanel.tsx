/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {ForbiddenError} from '#/shared/errors';
import {DiagramShell} from '#/operate/shared/DiagramShell/DiagramShell';
import {DecisionViewer} from '#/operate/shared/DecisionViewer';
import {useDecisionInstance} from './decisionInstance.queries';
import {useDecisionDefinitionXml} from './useDecisionDefinitionXml';
import {IncidentBanner, Section} from './styled';

type Props = {
	decisionEvaluationInstanceKey: string;
};

const DecisionPanel: React.FC<Props> = ({decisionEvaluationInstanceKey}) => {
	const {t} = useTranslation();
	const {query: decisionInstanceQuery} = useDecisionInstance(decisionEvaluationInstanceKey);
	const {
		data: decisionInstance,
		error: decisionInstanceLoadError,
		isPending: isDecisionInstancePending,
	} = decisionInstanceQuery;
	const {
		data: decisionDefinitionXml,
		isFetching: isDecisionDefinitionXmlFetching,
		isError: isDecisionDefinitionXmlError,
		error: decisionDefinitionXmlError,
	} = useDecisionDefinitionXml(decisionInstance?.decisionDefinitionKey);
	const matchedRules = decisionInstance?.matchedRules;
	const highlightableRules = useMemo(() => {
		if (matchedRules === undefined) {
			return [];
		}

		return Array.from(
			new Set(
				matchedRules.map(({ruleIndex}) => ruleIndex).filter((ruleIndex): ruleIndex is number => ruleIndex !== null),
			),
		);
	}, [matchedRules]);

	const panelStatus = (() => {
		if (isDecisionInstancePending || isDecisionDefinitionXmlFetching) {
			return 'loading';
		}
		if (decisionDefinitionXmlError instanceof ForbiddenError) {
			return 'forbidden';
		}
		if (isDecisionDefinitionXmlError || decisionInstanceLoadError !== null) {
			return 'error';
		}
		return 'content';
	})() satisfies React.ComponentProps<typeof DiagramShell>['status'];

	return (
		<Section data-testid="decision-panel" aria-label="decision panel" tabIndex={0}>
			{decisionInstance?.state === 'FAILED' && (
				<IncidentBanner data-testid="incident-banner">
					{decisionInstance.evaluationFailure ?? t('operate.decisionInstance.panel.unknownEvaluationFailure')}
				</IncidentBanner>
			)}
			<DiagramShell status={panelStatus}>
				<DecisionViewer
					xml={decisionDefinitionXml ?? null}
					decisionViewId={decisionInstance?.decisionDefinitionId ?? null}
					highlightableRules={highlightableRules}
				/>
			</DiagramShell>
		</Section>
	);
};

export {DecisionPanel};
