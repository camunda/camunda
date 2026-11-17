/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef, useState} from 'react';
import {ErrorBoundary} from 'react-error-boundary';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
import {Alert, AlertTitle, Button, IconButton} from '@camunda/design-system';
import {ChevronDown, ChevronUp} from '@camunda/design-system/icons';
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
	const [isFailureExpanded, setIsFailureExpanded] = useState(false);
	const [isFailureOverflowing, setIsFailureOverflowing] = useState(false);
	const failureAlertRef = useRef<HTMLDivElement>(null);
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

	const evaluationFailure = decisionInstance?.evaluationFailure;
	const isFailed = decisionInstance?.state === 'FAILED';
	const isFailureCollapsible = isFailureOverflowing || isFailureExpanded;

	useEffect(() => {
		const alert = failureAlertRef.current;
		if (!isFailed || alert === null || isFailureExpanded) {
			return;
		}
		const title = alert.querySelector<HTMLElement>('[data-slot=alert-title]');
		const measure = () => {
			if (title !== null) {
				setIsFailureOverflowing(title.scrollWidth > title.clientWidth);
			}
		};
		measure();
		const observer = new ResizeObserver(measure);
		observer.observe(alert);
		return () => observer.disconnect();
	}, [isFailed, evaluationFailure, isFailureExpanded]);

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
			className="flex h-full flex-col overflow-hidden"
		>
			{isFailed && (
				<div className="relative shrink-0">
					<Alert ref={failureAlertRef} data-testid="incident-banner" variant="destructive" className="rounded-none">
						<AlertTitle className={cn('mb-0', isFailureCollapsible && 'mr-8', !isFailureExpanded && 'truncate')}>
							{evaluationFailure ?? t('operate.decisionInstance.ds.panel.unknownEvaluationFailure')}
						</AlertTitle>
					</Alert>
					{isFailureCollapsible && (
						<IconButton
							type="button"
							variant="ghost"
							size="sm"
							className="absolute inset-y-0 right-3 my-auto active:not-aria-[haspopup]:translate-y-0 hover:bg-transparent active:bg-transparent aria-expanded:bg-transparent dark:hover:bg-transparent dark:active:bg-transparent dark:aria-expanded:bg-transparent"
							aria-expanded={isFailureExpanded}
							label={t(
								isFailureExpanded
									? 'operate.decisionInstance.ds.panel.collapseFailure'
									: 'operate.decisionInstance.ds.panel.expandFailure',
							)}
							icon={isFailureExpanded ? ChevronUp : ChevronDown}
							onClick={() => setIsFailureExpanded((expanded) => !expanded)}
						/>
					)}
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
					<ErrorBoundary
						resetKeys={[decisionDefinitionXml]}
						fallbackRender={({resetErrorBoundary}) => (
							<div className="flex grow flex-col items-center justify-center gap-4">
								<ErrorMessage />
								<Button
									type="button"
									variant="secondary"
									size="sm"
									onClick={() => {
										void refetchDecisionDefinitionXml().then(resetErrorBoundary);
									}}
								>
									{t('errorGenericErrorPageButtonLabel')}
								</Button>
							</div>
						)}
					>
						<DecisionViewer
							xml={decisionDefinitionXml ?? null}
							decisionViewId={decisionInstance?.decisionDefinitionId ?? null}
							highlightableRules={highlightableRules}
						/>
					</ErrorBoundary>
				</DiagramShell>
			)}
		</section>
	);
};

export {DecisionPanel};
