/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {useNavigate} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';
import {EmptyState} from '#/operate/components/EmptyState/shadcn.components/EmptyState';
import permissionDeniedIconUrl from '#/operate/assets/permission-denied.svg';
import {getProcessInstanceErrorKind} from '../processInstance.queries';

type Props = {
	error: unknown;
	processInstanceId: string;
	onRetry: () => void;
};

const ProcessInstanceError: React.FC<Props> = ({error, processInstanceId, onRetry}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const redirectedNotFoundId = useRef<string | null>(null);
	const errorKind = getProcessInstanceErrorKind(error);

	useEffect(() => {
		if (errorKind !== 'notFound' || redirectedNotFoundId.current === processInstanceId) {
			return;
		}
		redirectedNotFoundId.current = processInstanceId;
		notificationsStore.displayNotification({
			kind: 'error',
			title: t('operate.processInstance.notFoundNotificationTitle', {processInstanceId}),
			isDismissable: true,
		});
		void navigate({
			to: '/operate-preview/processes',
			search: {active: true, incidents: true, suspended: true, completed: false, canceled: false},
			replace: true,
		});
	}, [errorKind, processInstanceId, navigate, t]);

	if (errorKind === 'notFound') {
		return null;
	}

	return (
		<main id="main-content" tabIndex={-1} className="h-full outline-none">
			{errorKind === 'forbidden' ? (
				<EmptyState
					icon={<img src={permissionDeniedIconUrl} alt="" />}
					heading={t('operate.processInstance.ds.forbidden.heading')}
					description={t('operate.processInstance.forbidden.description')}
					link={{
						label: t('operate.processInstance.forbidden.learnMoreLink'),
						href: 'https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions',
					}}
				/>
			) : (
				<GenericErrorPage reset={onRetry} />
			)}
		</main>
	);
};

export {ProcessInstanceError};
