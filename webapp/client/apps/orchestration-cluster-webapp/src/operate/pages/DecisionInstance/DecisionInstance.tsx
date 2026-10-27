/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useRef} from 'react';
import {useLocation} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {VisuallyHiddenH1} from '#/operate/shared/VisuallyHiddenH1/VisuallyHiddenH1';
import {InstanceDetail} from '#/operate/shared/InstanceDetail/InstanceDetail';
import {DiagramShell} from '#/operate/shared/DiagramShell/DiagramShell';
import {DecisionInstanceError} from './DecisionInstanceError';
import {Header} from './Header';
import {DecisionPanel} from './DecisionPanel';
import {Drd} from './Drd';
import {DrdPanel} from './DrdPanel';
import {VariablesPanel, VariablesPanelPending} from './VariablesPanel';
import {useDecisionInstance} from './decisionInstance.queries';
import {Container, Section} from './styled';
import {useDrdPanelState} from './useDrdPanelState';

type Props = {
	decisionInstanceId: string;
};

const DecisionInstance: React.FC<Props> = ({decisionInstanceId}) => {
	const {t} = useTranslation();
	const {state} = useLocation();
	const [drdPanelState, setDrdPanelState] = useDrdPanelState();
	const shouldRestoreFocus = useRef(false);
	const lastFocusedDestination = useRef<{key: string; status: 'success' | 'error'} | null>(null);
	const {query} = useDecisionInstance(decisionInstanceId);
	const focusFromDrd =
		typeof state.operateDecisionFocus === 'object' &&
		state.operateDecisionFocus.decisionInstanceKey === decisionInstanceId;

	const changeDrdPanelState = (state: typeof drdPanelState) => {
		shouldRestoreFocus.current = state !== drdPanelState;
		setDrdPanelState(state);
	};

	const openDrdButtonRef = useCallback(
		(button: HTMLButtonElement | null) => {
			if (button !== null && drdPanelState === 'closed' && shouldRestoreFocus.current) {
				button.focus();
				shouldRestoreFocus.current = false;
			}
		},
		[drdPanelState],
	);

	const drdModeButtonRef = useCallback(
		(button: HTMLButtonElement | null) => {
			if (button !== null && drdPanelState !== 'closed' && shouldRestoreFocus.current) {
				button.focus();
				shouldRestoreFocus.current = false;
			}
		},
		[drdPanelState],
	);

	useEffect(() => {
		if (!focusFromDrd) {
			return;
		}
		const status = query.isError ? 'error' : query.isSuccess ? 'success' : null;
		if (
			status === null ||
			(lastFocusedDestination.current?.key === decisionInstanceId && lastFocusedDestination.current.status === status)
		) {
			return;
		}
		if (status === 'error') {
			lastFocusedDestination.current = {key: decisionInstanceId, status};
			return;
		}
		const target =
			document.getElementById('operate-decision-drd-mode-button') ??
			document.getElementById('operate-decision-instance-heading');
		if (target !== null) {
			target.focus();
			lastFocusedDestination.current = {key: decisionInstanceId, status};
		}
	}, [decisionInstanceId, focusFromDrd, query.isError, query.isSuccess]);

	if (query.isError) {
		return (
			<DecisionInstanceError
				error={query.error}
				decisionInstanceId={decisionInstanceId}
				onRetry={() => void query.refetch()}
			/>
		);
	}

	const drd =
		drdPanelState !== 'closed' ? (
			<Drd
				key={decisionInstanceId}
				decisionEvaluationInstanceKey={decisionInstanceId}
				decisionEvaluationKey={query.data?.decisionEvaluationKey}
				decisionDefinitionKey={query.data?.decisionDefinitionKey}
				decisionDefinitionId={query.data?.decisionDefinitionId}
				drdPanelState={drdPanelState}
				onChangeDrdPanelState={changeDrdPanelState}
				modeButtonRef={drdModeButtonRef}
			/>
		) : null;

	if (drdPanelState === 'maximized') {
		return (
			<>
				<VisuallyHiddenH1 id="operate-decision-instance-heading" tabIndex={-1}>
					{t('operate.decisionInstance.title')}
				</VisuallyHiddenH1>
				<Container>{drd}</Container>
			</>
		);
	}

	return (
		<DecisionInstanceShell
			header={
				<Header
					decisionEvaluationInstanceKey={decisionInstanceId}
					onOpenDrd={() => changeDrdPanelState('minimized')}
					openDrdButtonRef={openDrdButtonRef}
				/>
			}
			topPanel={<DecisionPanel decisionEvaluationInstanceKey={decisionInstanceId} />}
			bottomPanel={<VariablesPanel decisionEvaluationInstanceKey={decisionInstanceId} />}
			rightPanel={drdPanelState === 'minimized' ? <DrdPanel>{drd}</DrdPanel> : null}
		/>
	);
};

type ShellProps = {
	header: React.ReactNode;
	topPanel?: React.ReactNode;
	bottomPanel?: React.ReactNode;
	rightPanel?: React.ReactNode;
	isPending?: boolean;
};

const DecisionInstanceShell: React.FC<ShellProps> = ({header, topPanel, bottomPanel, rightPanel, isPending}) => {
	const {t} = useTranslation();
	const pendingTopPanel = (
		<Section aria-label={t('operate.decisionInstance.panel.label')} tabIndex={0}>
			<DiagramShell status="loading">{null}</DiagramShell>
		</Section>
	);

	return (
		<>
			<VisuallyHiddenH1 id="operate-decision-instance-heading" tabIndex={-1}>
				{t('operate.decisionInstance.title')}
			</VisuallyHiddenH1>
			<Container $isPending={isPending}>
				<InstanceDetail
					type="decision"
					header={header}
					topPanel={topPanel ?? pendingTopPanel}
					bottomPanel={bottomPanel ?? <VariablesPanelPending />}
					rightPanel={rightPanel}
				/>
			</Container>
		</>
	);
};

export {DecisionInstance, DecisionInstanceShell};
