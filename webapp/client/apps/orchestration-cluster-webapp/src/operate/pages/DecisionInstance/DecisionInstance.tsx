/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useRef} from 'react';
import {useLocation, useNavigate} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {GenericErrorPage} from '#/shared/pages/GenericErrorPage';
import {VisuallyHiddenH1} from '#/operate/shared/VisuallyHiddenH1/VisuallyHiddenH1';
import {InstanceDetail} from '#/operate/shared/InstanceDetail/InstanceDetail';
import {DiagramShell} from '#/operate/shared/DiagramShell/DiagramShell';
import {EmptyState} from '#/operate/components/EmptyState/EmptyState';
import permissionDeniedIconUrl from '#/operate/assets/permission-denied.svg';
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
	const navigate = useNavigate();
	const {state} = useLocation();
	const [drdPanelState, setDrdPanelState] = useDrdPanelState();
	const shouldRestoreFocus = useRef(false);
	const errorHeadingRef = useRef<HTMLHeadingElement>(null);
	const forbiddenHeadingRef = useRef<HTMLHeadingElement>(null);
	const lastFocusedDestination = useRef<{key: string; status: 'success' | 'error' | 'forbidden'} | null>(null);
	const redirectedNotFoundId = useRef<string | null>(null);
	const {isUnauthorized, isNotFound, isGenericError, query} = useDecisionInstance(decisionInstanceId);
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
		if (isNotFound && redirectedNotFoundId.current !== decisionInstanceId) {
			redirectedNotFoundId.current = decisionInstanceId;
			notificationsStore.displayNotification({
				kind: 'error',
				title: t('operate.decisionInstance.notFoundNotificationTitle', {decisionInstanceId}),
				isDismissable: true,
			});
			void navigate({
				to: '/operate/decisions',
				search: {evaluated: true, failed: true},
				replace: true,
				state: (state) => ({
					...state,
					operateDecisionFocus: focusFromDrd ? 'list' : undefined,
				}),
			});
		}
	}, [isNotFound, decisionInstanceId, focusFromDrd, navigate, t]);

	useEffect(() => {
		if (!focusFromDrd || isNotFound) {
			return;
		}
		const status = isGenericError ? 'error' : isUnauthorized ? 'forbidden' : query.isSuccess ? 'success' : null;
		if (
			status === null ||
			(lastFocusedDestination.current?.key === decisionInstanceId && lastFocusedDestination.current.status === status)
		) {
			return;
		}
		const target =
			status === 'error'
				? errorHeadingRef.current
				: status === 'forbidden'
					? forbiddenHeadingRef.current
					: (document.getElementById('operate-decision-drd-mode-button') ??
						document.getElementById('operate-decision-instance-heading'));
		if (target !== null) {
			target.focus();
			lastFocusedDestination.current = {key: decisionInstanceId, status};
		}
	}, [decisionInstanceId, focusFromDrd, isGenericError, isNotFound, isUnauthorized, query.isSuccess]);

	if (isUnauthorized) {
		return (
			<EmptyState
				headingRef={forbiddenHeadingRef}
				icon={<img src={permissionDeniedIconUrl} alt="" />}
				heading={t('operate.decisionInstance.forbidden.heading')}
				description={t('operate.decisionInstance.forbidden.description')}
				link={{
					label: t('operate.decisionInstance.forbidden.learnMoreLink'),
					href: 'https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions',
				}}
			/>
		);
	}

	if (isGenericError) {
		return <GenericErrorPage headingRef={errorHeadingRef} reset={() => void query.refetch()} />;
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
