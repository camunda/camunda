/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Button, EmptyState, PageHeader, PageLayout} from '@camunda/design-system';
import type {CurrentUser, ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {ProcessTile} from '#/tasklist/modules/processes/shadcn.components/ProcessTile';
import {ProcessesFilters} from '#/tasklist/modules/processes/shadcn.components/ProcessesFilters';
import type {ProcessesSearch} from '#/tasklist/modules/processes/searchSchema';

type Props = {
	initialFilterValues: ProcessesSearch;
	tenants: CurrentUser['tenants'];
	processes: ProcessDefinition[];
	hasNextPage: boolean;
	isFetchingNextPage: boolean;
	onLoadMore: () => void;
};

const TasklistProcessesPage: React.FC<Props> = ({
	initialFilterValues,
	tenants,
	processes,
	hasNextPage,
	isFetchingNextPage,
	onLoadMore,
}) => {
	const {t} = useTranslation();
	const isFiltered = initialFilterValues.search !== undefined && initialFilterValues.search !== '';

	return (
		<PageLayout>
			{/* Section spacing is done with `gap` on flex containers, not padding/margin utilities —
			    a legacy global CSS reset (from an unrelated Carbon dependency still loaded app-wide)
			    unconditionally zeroes padding/margin on plain elements, but leaves `gap` alone. */}
			<div className="flex flex-col gap-6">
				<PageHeader title={t('tasklist.headerNavItemProcesses')} description={t('tasklist.processesSubtitle')} />

				<div className="flex flex-col gap-4">
					<ProcessesFilters initialFilterValues={initialFilterValues} tenants={tenants} />

					{processes.length === 0 ? (
						<EmptyState
							heading={
								isFiltered
									? t('tasklist.processesProcessNotFoundError')
									: t('tasklist.processesProcessNotPublishedError')
							}
							description={t('tasklist.processesErrorBody')}
						/>
					) : (
						<div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
							{processes.map((process) => (
								<ProcessTile key={process.processDefinitionKey} process={process} />
							))}
						</div>
					)}

					{hasNextPage ? (
						<div className="flex justify-center">
							<Button type="button" variant="ghost" disabled={isFetchingNextPage} onClick={onLoadMore}>
								{isFetchingNextPage ? t('tasklist.processesLoadingMore') : t('tasklist.processesLoadMore')}
							</Button>
						</div>
					) : null}
				</div>
			</div>

			{/* TODO(#60229): start-process action / form modal mounts here via the nested route Outlet */}
		</PageLayout>
	);
};

export {TasklistProcessesPage};
