/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {t} from 'i18next';
import {BatchOperation, BatchOperationSkeleton} from '#/operate/pages/BatchOperation/BatchOperation';
import {getBatchOperationOptions} from '#/operate/pages/BatchOperation/batchOperation.queries';
import {formatOperationType} from '#/operate/pages/BatchOperation/utils';

export const Route = createFileRoute('/_carbon/_auth/operate/batch-operations/$batchOperationKey')({
	loader: async ({context: {queryClient}, params: {batchOperationKey}}) => {
		try {
			return await queryClient.ensureQueryData(getBatchOperationOptions(batchOperationKey));
		} catch {
			return undefined;
		}
	},
	head: ({loaderData}) => ({
		meta: loaderData
			? [{title: t('operate.batchOperation.pageTitle', {name: formatOperationType(loaderData.batchOperationType)})}]
			: [],
	}),
	pendingComponent: () => <BatchOperationSkeleton />,
	component: function BatchOperationRoute() {
		const {batchOperationKey} = Route.useParams();
		return <BatchOperation batchOperationKey={batchOperationKey} />;
	},
});
