/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {useNavigate} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {GenericErrorPage} from '#/shared/pages/GenericErrorPage';
import {VisuallyHiddenH1} from '#/operate/shared/VisuallyHiddenH1/VisuallyHiddenH1';
import {InstanceDetail} from '#/operate/shared/InstanceDetail/InstanceDetail';
import {EmptyState} from '#/operate/components/EmptyState/EmptyState';
import permissionDeniedIconUrl from '#/operate/assets/permission-denied.svg';
import {useCallHierarchy} from '#/operate/shared/Operations/Operations.queries';
import {ProcessInstanceContext} from './useProcessInstancePage';
import {ProcessInstanceHeader} from './ProcessInstanceHeader';
import {ProcessInstanceHeaderSkeleton} from './ProcessInstanceHeaderSkeleton';
import {ProcessInstanceBreadcrumb} from './ProcessInstanceBreadcrumb';
import {useProcessInstance} from './processInstance.queries';
import type {ProcessInstanceSearch} from './processInstanceSearch';
import {Container} from './styled';

type Props = {
	processInstanceId: string;
	search: ProcessInstanceSearch;
	topPanel?: React.ReactNode;
	bottomPanel?: React.ReactNode;
	headerOperations?: React.ReactNode;
};

const ProcessInstance: React.FC<Props> = ({processInstanceId, search, topPanel, bottomPanel, headerOperations}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const {query, isUnauthorized, isNotFound, isGenericError} = useProcessInstance(processInstanceId);
	const {data: processInstance, error, refetch} = query;
	const {data: hierarchy} = useCallHierarchy(processInstanceId, {
		enabled: processInstance !== undefined && error === null,
	});

	useEffect(() => {
		if (isNotFound) {
			notificationsStore.displayNotification({
				kind: 'error',
				title: t('operate.processInstance.notFoundNotificationTitle', {processInstanceId}),
				isDismissable: true,
			});
			void navigate({
				to: '/operate/processes',
				search: {active: true, incidents: true, suspended: true, completed: false, canceled: false},
				replace: true,
			});
		}
	}, [isNotFound, processInstanceId, navigate, t]);

	if (isUnauthorized) {
		return (
			<Container>
				<EmptyState
					icon={<img src={permissionDeniedIconUrl} alt="" />}
					heading={t('operate.processInstance.forbidden.heading')}
					description={t('operate.processInstance.forbidden.description')}
					link={{
						label: t('operate.processInstance.forbidden.learnMoreLink'),
						href: 'https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions',
					}}
				/>
			</Container>
		);
	}

	if (isNotFound) {
		return null;
	}

	if (isGenericError) {
		return (
			<Container>
				<GenericErrorPage reset={() => void refetch()} />
			</Container>
		);
	}

	if (processInstance === undefined) {
		return <ProcessInstancePending />;
	}

	return (
		<ProcessInstanceContext
			value={{
				processInstanceId,
				processInstance,
				search,
				selection: {
					elementId: search.elementId,
					elementInstanceKey: search.elementInstanceKey,
					isMultiInstanceBody: search.isMultiInstanceBody,
					isPlaceholder: search.isPlaceholder,
					anchorElementId: search.anchorElementId,
				},
			}}
		>
			<Container>
				<VisuallyHiddenH1>{t('operate.processInstance.title')}</VisuallyHiddenH1>
				<InstanceDetail
					type="process"
					breadcrumb={
						hierarchy && hierarchy.length > 0 ? (
							<ProcessInstanceBreadcrumb callHierarchy={hierarchy.slice(0, -1)} processInstance={processInstance} />
						) : undefined
					}
					header={<ProcessInstanceHeader operations={headerOperations} />}
					topPanel={topPanel ?? <div />}
					bottomPanel={bottomPanel ?? <div />}
				/>
			</Container>
		</ProcessInstanceContext>
	);
};

const ProcessInstancePending: React.FC = () => {
	const {t} = useTranslation();

	return (
		<Container>
			<VisuallyHiddenH1>{t('operate.processInstance.title')}</VisuallyHiddenH1>
			<InstanceDetail
				type="process"
				header={<ProcessInstanceHeaderSkeleton />}
				topPanel={<div />}
				bottomPanel={<div />}
			/>
		</Container>
	);
};

export {ProcessInstance, ProcessInstancePending};
