/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import {useTranslation} from 'react-i18next';
import {Button, EmptyState, PageHeader, PageLayout} from '@camunda/design-system';
import type {CurrentUser, ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {ProcessTile} from '#/tasklist/modules/processes/shadcn.components/ProcessTile';
import {ProcessesFilters} from '#/tasklist/modules/processes/shadcn.components/ProcessesFilters';
import {FirstTimeProcessWarning} from '#/tasklist/modules/processes/shadcn.components/FirstTimeProcessWarning';
import {useStartProcess} from '#/tasklist/modules/processes/useStartProcess';
import type {ProcessesSearch} from '#/tasklist/modules/processes/searchSchema';

type Props = {
	initialFilterValues: ProcessesSearch;
	tenants: CurrentUser['tenants'];
	processes: ProcessDefinition[];
	hasNextPage: boolean;
	isFetchingNextPage: boolean;
	onLoadMore: () => void;
	onOpenStartProcessForm: (processDefinitionKey: string) => void;
	children?: React.ReactNode;
};

const TasklistProcessesPage: React.FC<Props> = ({
	initialFilterValues,
	tenants,
	processes,
	hasNextPage,
	isFetchingNextPage,
	onLoadMore,
	onOpenStartProcessForm,
	children,
}) => {
	const {t} = useTranslation();
	const isFiltered = initialFilterValues.search !== undefined && initialFilterValues.search !== '';
	const {status, selectedProcessDefinitionKey, isBusy, startProcess} = useStartProcess();
	const handleStartProcess = useCallback(
		(process: ProcessDefinition) => {
			if (process.hasStartForm) {
				onOpenStartProcessForm(process.processDefinitionKey);
				return;
			}

			startProcess(process);
		},
		[onOpenStartProcessForm, startProcess],
	);

	return (
		<>
			<PageLayout>
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
								description={
									<>
										{t('tasklist.processesErrorBody')}
										<Button asChild variant="link" className="h-auto p-0 align-baseline font-normal">
											<a
												href="https://docs.camunda.io/docs/components/modeler/web-modeler/run-or-publish-your-process/#publishing-a-process"
												target="_blank"
												rel="noopener noreferrer"
											>
												{t('tasklist.processesErrorBodyLinkLabel')}
											</a>
										</Button>
									</>
								}
							/>
						) : (
							<div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
								{processes.map((process) => {
									const isSelected = selectedProcessDefinitionKey === process.processDefinitionKey;

									return (
										<ProcessTile
											key={process.processDefinitionKey}
											process={process}
											status={isSelected ? status : 'inactive'}
											isStartButtonDisabled={isBusy && !isSelected}
											onStartProcess={() => handleStartProcess(process)}
										/>
									);
								})}
							</div>
						)}

						{hasNextPage && processes.length > 0 ? (
							<div className="flex justify-center">
								<Button type="button" variant="ghost" disabled={isFetchingNextPage} onClick={onLoadMore}>
									{isFetchingNextPage ? t('tasklist.processesLoadingMore') : t('tasklist.processesLoadMore')}
								</Button>
							</div>
						) : null}
					</div>
				</div>
			</PageLayout>
			<FirstTimeProcessWarning>{children}</FirstTimeProcessWarning>
		</>
	);
};

export {TasklistProcessesPage};
