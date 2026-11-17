/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {t} from 'i18next';
import {DataTable, type DataTableColumn} from '@camunda/design-system';
import {OperationsLog} from '#/operate/pages/OperationsLog/shadcn.components/OperationsLog';
import {operationsLogSearchSchema, stripLegacyFilters} from '#/operate/pages/OperationsLog/operationsLog.schema';

const SKELETON_COLUMNS: DataTableColumn<never>[] = Array.from({length: 7}, (_, index) => ({
	id: `skeleton-${index}`,
	header: '',
	cell: () => null,
}));

export const Route = createFileRoute('/_shadcn/_auth/operate-preview/operations-log')({
	validateSearch: operationsLogSearchSchema,
	search: {middlewares: [stripLegacyFilters]},
	head: () => ({meta: [{title: t('operate.operationsLog.pageTitle')}]}),
	pendingComponent: () => (
		<DataTable
			columns={SKELETON_COLUMNS}
			data={[]}
			aria-label={t('operate.operationsLog.table.tableLabel')}
			loading
			loadingRowCount={5}
		/>
	),
	component: function OperationsLogRoute() {
		return <OperationsLog {...Route.useSearch()} />;
	},
});
