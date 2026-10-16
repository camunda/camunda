/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {Button} from '@camunda/design-system';
import {ForbiddenError} from '#/shared/errors';
import {DiagramShell} from '#/operate/shared/DiagramShell/shadcn.components/DiagramShell';
import {DecisionViewer} from '#/operate/shared/DecisionViewer/shadcn.components/DecisionViewer';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/shadcn.components/ErrorMessage';
import {useDecisionInstance} from '../decisionInstance.queries';
import {getHighlightableRules} from '../getHighlightableRules';
import {useDecisionDefinitionXml} from '../useDecisionDefinitionXml';

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
		refetch: refetchDecisionDefinitionXml,
	} = useDecisionDefinitionXml(decisionInstance?.decisionDefinitionKey);
	const matchedRules = decisionInstance?.matchedRules;
	const highlightableRules = useMemo(() => getHighlightableRules(matchedRules), [matchedRules]);

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
		<section
			data-testid="decision-panel"
			aria-label={t('operate.decisionInstance.panel.label')}
			tabIndex={0}
			className="h-full overflow-auto"
		>
			{decisionInstance?.state === 'FAILED' && (
				<div
					data-testid="incident-banner"
					className="flex h-12 items-center justify-center border border-l-4 border-danger-border-strong bg-danger-background-subtle text-sm text-neutral-foreground"
				>
					{decisionInstance.evaluationFailure ?? t('operate.decisionInstance.panel.unknownEvaluationFailure')}
				</div>
			)}
			{panelStatus === 'error' && isDecisionDefinitionXmlError ? (
				<DiagramShell status="content">
					<div className="flex grow flex-col items-center justify-center gap-4">
						<ErrorMessage />
						<Button type="button" variant="secondary" size="sm" onClick={() => void refetchDecisionDefinitionXml()}>
							{t('errorGenericErrorPageButtonLabel')}
						</Button>
					</div>
				</DiagramShell>
			) : (
				<DiagramShell status={panelStatus}>
					<DecisionViewer
						xml={decisionDefinitionXml ?? null}
						decisionViewId={decisionInstance?.decisionDefinitionId ?? null}
						highlightableRules={highlightableRules}
					/>
				</DiagramShell>
			)}
		</section>
	);
};

export {DecisionPanel};
