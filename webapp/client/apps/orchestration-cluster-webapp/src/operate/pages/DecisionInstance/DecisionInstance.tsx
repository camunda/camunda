/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useRef} from 'react';
import {useNavigate} from '@tanstack/react-router';
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
	const [drdPanelState, setDrdPanelState] = useDrdPanelState();
	const shouldRestoreFocus = useRef(false);
	const {isUnauthorized, isNotFound, isGenericError, query} = useDecisionInstance(decisionInstanceId);

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
		if (isNotFound) {
			notificationsStore.displayNotification({
				kind: 'error',
				title: t('operate.decisionInstance.notFoundNotificationTitle', {decisionInstanceId}),
				isDismissable: true,
			});
			void navigate({to: '/operate/decisions', search: {evaluated: true, failed: true}, replace: true});
		}
	}, [isNotFound, decisionInstanceId, navigate, t]);

	if (isUnauthorized) {
		return (
			<EmptyState
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
		return <GenericErrorPage reset={() => void query.refetch()} />;
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
};

const DecisionInstanceShell: React.FC<ShellProps> = ({header, topPanel, bottomPanel, rightPanel}) => {
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
			<Container>
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
