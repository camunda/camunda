/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {IconButton, SkeletonText, InlineNotification} from '@carbon/react';
import {ArrowLeft} from '@carbon/react/icons';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {ForbiddenError} from '#/shared/errors';
import {requestErrorSchema} from '#/shared/http/request';
import {VisuallyHiddenH1} from '#/operate/shared/VisuallyHiddenH1/VisuallyHiddenH1';
import {BatchStateIndicator} from '#/operate/shared/BatchStateIndicator';
import {BatchItemsCount} from '#/operate/shared/BatchItemsCount';
import {EmptyState} from '#/operate/components/EmptyState/EmptyState';
import permissionDeniedIconUrl from '#/operate/assets/permission-denied.svg';
import {useBatchOperation} from './batchOperation.queries';
import {formatOperationType, formatDate} from './utils';
import {BatchItemsTable} from './BatchItemsTable';
import {PageContainer, Header, HeaderTitleContainer, TilesContainer, Tile, TileLabel} from './styled';

const TILE_LABEL_KEYS = [
	'operate.batchOperation.tiles.state',
	'operate.batchOperation.tiles.summaryOfItems',
	'operate.batchOperation.tiles.startDate',
	'operate.batchOperation.tiles.endDate',
	'operate.batchOperation.tiles.actor',
] as const;

const BatchOperationSkeleton: React.FC = () => {
	const {t} = useTranslation();

	return (
		<PageContainer gap={5}>
			<VisuallyHiddenH1>{t('operate.batchOperation.title')}</VisuallyHiddenH1>
			<Header>
				<HeaderTitleContainer>
					<SkeletonText data-testid="text-skeleton" />
				</HeaderTitleContainer>
			</Header>
			<TilesContainer gap={4} orientation="horizontal">
				{TILE_LABEL_KEYS.map((labelKey) => (
					<Tile key={labelKey}>
						<TileLabel>{t(labelKey)}</TileLabel>
						<SkeletonText data-testid="text-skeleton" />
					</Tile>
				))}
			</TilesContainer>
		</PageContainer>
	);
};

type Props = {
	batchOperationKey: string;
};

const BatchOperation: React.FC<Props> = ({batchOperationKey}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const {data, error} = useBatchOperation(batchOperationKey);

	const requestError = requestErrorSchema.safeParse(error);
	const isUnauthorized = error instanceof ForbiddenError;
	const isNotFound = requestError.success && requestError.data.response?.status === 404;

	useEffect(() => {
		if (isNotFound) {
			notificationsStore.displayNotification({
				kind: 'error',
				title: t('operate.batchOperation.notFoundNotificationTitle', {batchOperationKey}),
				isDismissable: true,
			});
			void navigate({to: '/operate/batch-operations', replace: true});
		}
	}, [isNotFound, batchOperationKey, navigate, t]);

	if (isUnauthorized) {
		return (
			<EmptyState
				icon={<img src={permissionDeniedIconUrl} alt="" />}
				heading={t('operate.batchOperation.forbidden.heading')}
				description={t('operate.batchOperation.forbidden.description')}
				link={{
					label: t('operate.batchOperation.forbidden.learnMoreLink'),
					href: 'https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions',
				}}
			/>
		);
	}

	const operationType = formatOperationType(data?.batchOperationType ?? '');

	const tileData = [
		{
			label: t('operate.batchOperation.tiles.state'),
			content: data ? <BatchStateIndicator state={data.state} /> : null,
		},
		{
			label: t('operate.batchOperation.tiles.summaryOfItems'),
			content: (
				<BatchItemsCount
					totalCount={data?.operationsTotalCount ?? 0}
					completedCount={data?.operationsCompletedCount ?? 0}
					failedCount={data?.operationsFailedCount ?? 0}
				/>
			),
		},
		{
			label: t('operate.batchOperation.tiles.startDate'),
			content: formatDate(data?.startDate),
		},
		{
			label: t('operate.batchOperation.tiles.endDate'),
			content: formatDate(data?.endDate),
		},
		{
			label: t('operate.batchOperation.tiles.actor'),
			content: data?.actorId ?? '--',
		},
	];

	return (
		<PageContainer gap={5}>
			<VisuallyHiddenH1>{t('operate.batchOperation.title')}</VisuallyHiddenH1>
			<Header>
				<HeaderTitleContainer>
					<IconButton
						kind="ghost"
						size="md"
						label={t('operate.batchOperation.backButton')}
						align="bottom-start"
						onClick={() => navigate({to: '/operate/batch-operations'})}
					>
						<ArrowLeft />
					</IconButton>
					<h3>{operationType}</h3>
				</HeaderTitleContainer>
			</Header>
			{error && (
				<InlineNotification
					kind="error"
					statusIconDescription="notification"
					hideCloseButton
					role="alert"
					title={t('operate.batchOperation.loadErrorTitle')}
				/>
			)}
			<TilesContainer gap={4} orientation="horizontal">
				{tileData.map(({label, content}) => (
					<Tile key={label}>
						<TileLabel>{label}</TileLabel>
						{content}
					</Tile>
				))}
			</TilesContainer>
			<BatchItemsTable batchOperationKey={batchOperationKey} batchOperationType={data?.batchOperationType} />
		</PageContainer>
	);
};

export {BatchOperation, BatchOperationSkeleton};
