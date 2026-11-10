/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OPERATE_BASE} from '#/shared/operateBase';
import {useEffect} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {Alert, Heading, IconButton, PageLayout, Skeleton, Text} from '@camunda/design-system';
import {ArrowLeft} from '@camunda/design-system/icons';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {ForbiddenError} from '#/shared/errors';
import {requestErrorSchema} from '#/shared/http/request';
import {BatchStateIndicator} from '#/operate/shared/BatchStateIndicator/shadcn.components/BatchStateIndicator';
import {BatchItemsCount} from '#/operate/shared/BatchItemsCount/shadcn.components/BatchItemsCount';
import {EmptyState} from '#/operate/components/EmptyState/shadcn.components/EmptyState';
import permissionDeniedIconUrl from '#/operate/assets/permission-denied.svg';
import {useBatchOperation} from '../batchOperation.queries';
import {formatOperationType, formatDate} from '../utils';
import {notifyBatchOperationGoneOnce, clearBatchOperationGoneNotified} from '../useBatchOperationActions';
import {BatchItemsTable} from './BatchItemsTable';
import {BatchOperationActions} from './BatchOperationActions';

const TILE_LABEL_KEYS = [
	'operate.batchOperation.tiles.state',
	'operate.batchOperation.tiles.summaryOfItems',
	'operate.batchOperation.tiles.startDate',
	'operate.batchOperation.tiles.endDate',
	'operate.batchOperation.tiles.actor',
] as const;

type TileProps = {
	label: string;
	children: React.ReactNode;
};

const Tile: React.FC<TileProps> = ({label, children}) => (
	<div className="min-w-[200px] rounded-md border border-border bg-background p-4">
		<Text as="span" variant="label-sm" className="mb-3 block text-neutral-foreground-subtle">
			{label}
		</Text>
		{children}
	</div>
);

const BatchOperationHeading: React.FC = () => {
	const {t} = useTranslation();

	return (
		<Heading as="h1" className="sr-only">
			{t('operate.batchOperation.title')}
		</Heading>
	);
};

const BatchOperationSkeleton: React.FC = () => {
	const {t} = useTranslation();

	return (
		<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
			<div className="flex h-full flex-col gap-4 px-4 pt-12">
				<BatchOperationHeading />
				<div className="flex items-center justify-between">
					<Skeleton data-testid="text-skeleton" className="h-6 w-48" />
				</div>
				<div className="flex gap-3 self-start">
					{TILE_LABEL_KEYS.map((labelKey) => (
						<Tile key={labelKey} label={t(labelKey)}>
							<Skeleton data-testid="text-skeleton" className="h-4 w-24" />
						</Tile>
					))}
				</div>
			</div>
		</PageLayout>
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
		clearBatchOperationGoneNotified(batchOperationKey);
	}, [batchOperationKey]);

	useEffect(() => {
		if (isNotFound) {
			notifyBatchOperationGoneOnce(batchOperationKey, () =>
				notificationsStore.displayNotification({
					kind: 'error',
					title: t('operate.batchOperation.notFoundNotificationTitle', {batchOperationKey}),
					isDismissable: true,
				}),
			);
			void navigate({to: `${OPERATE_BASE}/batch-operations` as const, replace: true});
		}
	}, [isNotFound, batchOperationKey, navigate, t]);

	if (isUnauthorized) {
		return (
			<main id="main-content" tabIndex={-1} className="h-full outline-none">
				<EmptyState
					icon={<img src={permissionDeniedIconUrl} alt="" />}
					heading={t('operate.batchOperation.forbidden.heading')}
					description={t('operate.batchOperation.forbidden.description')}
					link={{
						label: t('operate.batchOperation.forbidden.learnMoreLink'),
						href: 'https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions',
					}}
				/>
			</main>
		);
	}

	return (
		<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
			<div className="flex h-full flex-col gap-4 px-4 pt-12">
				<BatchOperationHeading />
				<div className="flex items-center justify-between">
					<div className="flex items-center gap-1">
						<IconButton
							variant="ghost"
							icon={ArrowLeft}
							label={t('operate.batchOperation.backButton')}
							tooltipSide="bottom"
							onClick={() => navigate({to: `${OPERATE_BASE}/batch-operations` as const})}
						/>
						<Heading as="h2" variant="heading-md">
							{formatOperationType(data?.batchOperationType)}
						</Heading>
					</div>
					{data && (
						<BatchOperationActions
							key={batchOperationKey}
							batchOperationKey={batchOperationKey}
							batchOperationState={data.state}
						/>
					)}
				</div>
				{error && !isNotFound && <Alert variant="destructive" title={t('operate.batchOperation.loadErrorTitle')} />}
				<div className="flex gap-3 self-start">
					<Tile label={t('operate.batchOperation.tiles.state')}>
						{data ? <BatchStateIndicator state={data.state} /> : null}
					</Tile>
					<Tile label={t('operate.batchOperation.tiles.summaryOfItems')}>
						<BatchItemsCount
							totalCount={data?.operationsTotalCount ?? 0}
							completedCount={data?.operationsCompletedCount ?? 0}
							failedCount={data?.operationsFailedCount ?? 0}
						/>
					</Tile>
					<Tile label={t('operate.batchOperation.tiles.startDate')}>{formatDate(data?.startDate)}</Tile>
					<Tile label={t('operate.batchOperation.tiles.endDate')}>{formatDate(data?.endDate)}</Tile>
					<Tile label={t('operate.batchOperation.tiles.actor')}>
						<span>{data?.actorId ?? '--'}</span>
					</Tile>
				</div>
				<BatchItemsTable
					key={batchOperationKey}
					batchOperationKey={batchOperationKey}
					batchOperationType={data?.batchOperationType}
				/>
			</div>
		</PageLayout>
	);
};

export {BatchOperation, BatchOperationSkeleton};
