/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {Button} from '@carbon/react';
import {useQuery} from '@tanstack/react-query';
import {useNavigate} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {ForbiddenError} from '#/shared/errors';
import {requestErrorSchema} from '#/shared/http/request';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {VisuallyHiddenH1} from '#/operate/shared/VisuallyHiddenH1/VisuallyHiddenH1';
import {InstanceDetail} from '#/operate/shared/InstanceDetail/InstanceDetail';
import {ProcessInstanceHeaderSkeleton} from './ProcessInstanceHeaderSkeleton';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {EmptyState} from '#/operate/components/EmptyState/EmptyState';
import permissionDeniedIconUrl from '#/operate/assets/permission-denied.svg';
import {useCallHierarchy} from '#/operate/shared/Operations/Operations.queries';
import {processInstanceQuery} from './processInstance.queries';
import {ProcessInstanceProvider} from './ProcessInstanceProvider';
import {ProcessInstanceHeader} from './ProcessInstanceHeader';
import {ProcessInstanceBreadcrumb} from './ProcessInstanceBreadcrumb';
import type {ProcessInstanceSearch} from './processInstanceSearch';
import {Container} from './styled';
import {getProcessDefinitionName} from '#/operate/shared/utils/instance';

type Props = {
	processInstanceId: string;
	search: ProcessInstanceSearch;
	topPanel?: React.ReactNode;
	bottomPanel?: React.ReactNode;
	headerOperations?: React.ReactNode;
};

function ProcessInstance({processInstanceId, search, topPanel, bottomPanel, headerOperations}: Props) {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const {data: instance, error, isPending, refetch, isFetching} = useQuery(processInstanceQuery(processInstanceId));
	const {data: hierarchy} = useCallHierarchy(processInstanceId, {enabled: instance !== undefined && error === null});
	const parsedError = requestErrorSchema.safeParse(error);
	const isNotFound = parsedError.success && parsedError.data.response?.status === 404;
	useEffect(() => {
		if (isNotFound) {
			notificationsStore.displayNotification({
				kind: 'error',
				title: t('operate.processInstance.notFound', {processInstanceId}),
				isDismissable: true,
			});
			void navigate({
				to: '/operate/processes',
				search: {active: true, incidents: true, completed: false, canceled: false},
				replace: true,
			});
		}
	}, [isNotFound, processInstanceId, navigate, t]);
	useEffect(() => {
		if (instance) {
			const previousTitle = document.title;
			document.title = t('operate.processInstance.pageTitle', {
				processInstanceId,
				name: getProcessDefinitionName(instance),
			});
			return () => {
				document.title = previousTitle;
			};
		}
		return undefined;
	}, [instance, processInstanceId, t]);
	if (error instanceof ForbiddenError) {
		return (
			<Container>
				<EmptyState
					icon={<img src={permissionDeniedIconUrl} alt="" />}
					heading={t('operate.processInstance.forbidden.heading')}
					description={t('operate.processInstance.forbidden.description')}
					link={{
						label: t('operate.processInstance.forbidden.learnMore'),
						href: 'https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions',
					}}
				/>
			</Container>
		);
	}
	if (isNotFound) {
		return null;
	}
	if (error !== null && instance === undefined) {
		return (
			<Container>
				<ErrorMessage />
				<Button disabled={isFetching} onClick={() => void refetch()}>
					{t('operate.processInstance.retry')}
				</Button>
			</Container>
		);
	}
	if (isPending) {
		return <ProcessInstancePending />;
	}
	return (
		<ProcessInstanceProvider key={processInstanceId} processInstance={instance} search={search}>
			<Container>
				<VisuallyHiddenH1>{t('operate.processInstance.title')}</VisuallyHiddenH1>
				<InstanceDetail
					type="process"
					breadcrumb={
						hierarchy && hierarchy.length > 0 ? (
							<ProcessInstanceBreadcrumb callHierarchy={hierarchy.slice(0, -1)} processInstance={instance} />
						) : undefined
					}
					header={<ProcessInstanceHeader operations={headerOperations} />}
					topPanel={topPanel ?? <div />}
					bottomPanel={bottomPanel ?? <div />}
				/>
			</Container>
		</ProcessInstanceProvider>
	);
}

function ProcessInstancePending() {
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
}

export {ProcessInstance, ProcessInstancePending};
