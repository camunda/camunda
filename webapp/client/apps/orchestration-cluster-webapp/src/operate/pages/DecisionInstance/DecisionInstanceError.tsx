/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {useLocation, useNavigate} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {GenericErrorPage} from '#/shared/pages/GenericErrorPage';
import {EmptyState} from '#/operate/components/EmptyState/EmptyState';
import permissionDeniedIconUrl from '#/operate/assets/permission-denied.svg';
import {getDecisionInstanceErrorKind} from './decisionInstance.queries';

type Props = {
	error: unknown;
	decisionInstanceId: string;
	onRetry: () => void;
};

const DecisionInstanceError: React.FC<Props> = ({error, decisionInstanceId, onRetry}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const {state} = useLocation();
	const headingRef = useRef<HTMLHeadingElement>(null);
	const redirectedNotFoundId = useRef<string | null>(null);
	const errorKind = getDecisionInstanceErrorKind(error);
	const focusFromDrd =
		typeof state.operateDecisionFocus === 'object' &&
		state.operateDecisionFocus.decisionInstanceKey === decisionInstanceId;

	useEffect(() => {
		if (errorKind !== 'notFound' || redirectedNotFoundId.current === decisionInstanceId) {
			return;
		}
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
	}, [errorKind, decisionInstanceId, focusFromDrd, navigate, t]);

	useEffect(() => {
		if (focusFromDrd && errorKind !== 'notFound') {
			headingRef.current?.focus();
		}
	}, [decisionInstanceId, errorKind, focusFromDrd]);

	if (errorKind === 'notFound') {
		return null;
	}

	if (errorKind === 'forbidden') {
		return (
			<EmptyState
				headingRef={headingRef}
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

	return <GenericErrorPage headingRef={headingRef} reset={onRetry} />;
};

export {DecisionInstanceError};
