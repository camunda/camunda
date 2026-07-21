/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {useNavigate} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {tracking} from '#/shared/tracking';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {requestErrorSchema} from '#/shared/http/request';
import {VisuallyHiddenH1} from '#/operate/shared/VisuallyHiddenH1/VisuallyHiddenH1';
import {InstanceDetail} from '#/operate/shared/InstanceDetail/InstanceDetail';
import {EmptyState} from '#/operate/components/EmptyState/EmptyState';
import permissionDeniedIconUrl from '#/operate/assets/permission-denied.svg';
import {Header} from './Header';
import {useDecisionInstance} from './decisionInstance.queries';
import {Container} from './styled';

type Props = {
	decisionInstanceId: string;
};

const DecisionInstance: React.FC<Props> = ({decisionInstanceId}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const [drdPanelState, setDrdPanelState] = useState<'minimized' | 'closed'>('minimized');
	const {data: decisionInstance, error, status} = useDecisionInstance(decisionInstanceId);

	const requestError = requestErrorSchema.safeParse(error);
	const isUnauthorized = requestError.success && requestError.data.response?.status === 403;
	const isNotFound = requestError.success && requestError.data.response?.status === 404;

	useEffect(() => {
		if (decisionInstanceId !== '' && decisionInstance?.decisionDefinitionName !== undefined) {
			document.title = t('operate.decisionInstance.pageTitle', {
				decisionInstanceId,
				name: decisionInstance.decisionDefinitionName,
			});
		}
	}, [decisionInstanceId, decisionInstance?.decisionDefinitionName, t]);

	useEffect(() => {
		if (status === 'success' && decisionInstance.state !== undefined) {
			tracking.track({eventName: 'operate:decision-instance-details-loaded', state: decisionInstance.state});
		}
	}, [status, decisionInstance?.state]);

	useEffect(() => {
		if (isNotFound) {
			notificationsStore.displayNotification({
				kind: 'error',
				title: t('operate.decisionInstance.notFoundNotificationTitle', {decisionInstanceId}),
				isDismissable: true,
			});
			void navigate({to: '/operate/decisions', replace: true});
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

	return (
		<>
			<VisuallyHiddenH1>{t('operate.decisionInstance.title')}</VisuallyHiddenH1>
			<Container>
				<InstanceDetail
					type="decision"
					header={
						<Header
							decisionEvaluationInstanceKey={decisionInstanceId}
							onOpenDrd={() => setDrdPanelState('minimized')}
						/>
					}
					topPanel={<div />}
					bottomPanel={<div />}
					rightPanel={drdPanelState === 'minimized' ? <div /> : null}
				/>
			</Container>
		</>
	);
};

export {DecisionInstance};
