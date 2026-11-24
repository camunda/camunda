/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {Button} from '@camunda/design-system';
import {ShieldAlert} from '@camunda/design-system/icons';
import {useNavigate} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';
import {PageEmptyState} from '#/shared/pages/shadcn.components/PageEmptyState';
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
			autoRemove: false,
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
				<PageEmptyState
					icon={<ShieldAlert aria-hidden />}
					heading={t('operate.processInstance.ds.forbidden.heading')}
					description={t('operate.processInstance.forbidden.description')}
					secondaryAction={
						<Button variant="link" asChild>
							<a
								href="https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions"
								target="_blank"
								rel="noreferrer"
							>
								{t('operate.processInstance.forbidden.learnMoreLink')}
							</a>
						</Button>
					}
				/>
			) : (
				<GenericErrorPage reset={onRetry} />
			)}
		</main>
	);
};

export {ProcessInstanceError};
