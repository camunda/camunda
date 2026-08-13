/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import EmptyMessageImage from '#/tasklist/modules/processes/empty-message-image.svg';
import {ProcessesFilters} from '#/tasklist/modules/processes/components/ProcessesFilters';
import {ProcessTile} from '#/tasklist/modules/processes/components/ProcessTile';
import {FirstTimeProcessWarning} from '#/tasklist/modules/processes/components/FirstTimeProcessWarning';
import {useStartProcess} from '#/tasklist/modules/processes/useStartProcess';
import {C3EmptyState} from '@camunda/camunda-composite-components';
import {Button, Column, Grid, Layer, Link, Stack} from '#/shared/design-system-compat';
import {EmptyState} from '@camunda/design-system';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
import type {CurrentUser, ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {useTranslation} from 'react-i18next';
import {useCallback} from 'react';
import type {ProcessesSearch} from '#/tasklist/modules/processes/searchSchema';
import styles from './TasklistProcessesPage.module.scss';

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
	const {status, selectedProcessDefinitionKey, isBusy, startProcess} = useStartProcess();
	const isFiltered = initialFilterValues.search !== undefined && initialFilterValues.search !== '';
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
			<main
				id="main-content"
				className={cn('cds--content', styles.page, featureFlags.dsTasklistUI && styles.pageDS)}
			>
				<div className={styles.scrollContainer}>
					<Stack gap={2} className={cn(featureFlags.dsTasklistUI && styles.sectionsDS)}>
						<section
							className={cn(styles.header, featureFlags.dsTasklistUI && styles.headerDS)}
							aria-labelledby="processes-heading"
						>
							<Stack
								className={cn(styles.headerContent, featureFlags.dsTasklistUI && styles.headerContentDS)}
								gap={6}
							>
								{/* Carbon's Grid/Column only spanned the full width here, so the DS path
								    drops the grid entirely rather than reproducing a single full-span cell.
								    Type scale and the title/description gap follow the DS PageHeader. */}
								{featureFlags.dsTasklistUI ? (
									<Stack className={styles.titleBlockDS}>
										<h1 id="processes-heading" className={styles.pageTitleDS}>
											{t('tasklist.headerNavItemProcesses')}
										</h1>
										<p className={styles.pageDescriptionDS}>{t('tasklist.processesSubtitle')}</p>
									</Stack>
								) : (
									<Grid narrow>
										<Column sm={4} md={8} lg={16}>
											<Stack gap={4}>
												<h1 id="processes-heading">{t('tasklist.headerNavItemProcesses')}</h1>
												<p>{t('tasklist.processesSubtitle')}</p>
											</Stack>
										</Column>
									</Grid>
								)}

								<ProcessesFilters initialFilterValues={initialFilterValues} tenants={tenants} />
							</Stack>
						</section>

						<section className={cn(styles.processes, featureFlags.dsTasklistUI && styles.processesDS)}>
							<div className={cn(styles.processesContent, featureFlags.dsTasklistUI && styles.processesContentDS)}>
								{processes.length === 0 ? (
									<Layer>
										{/* DS-only: the DS EmptyState, matching NoTasks.tsx and the variables panel.
										    C3EmptyState is a composite with no compat adapter, so it stays on the
										    flag-off path. The illustration is dropped on the DS path — EmptyState
										    takes a node `icon`, not C3's `{path, altText}` image descriptor. */}
										{featureFlags.dsTasklistUI ? (
											<EmptyState
												heading={
													isFiltered
														? t('tasklist.processesProcessNotFoundError')
														: t('tasklist.processesProcessNotPublishedError')
												}
												description={
													<span>
														{t('tasklist.processesErrorBody')}
														<Link
															href="https://docs.camunda.io/docs/components/modeler/web-modeler/run-or-publish-your-process/#publishing-a-process"
															target="_blank"
															rel="noopener noreferrer"
															inline
														>
															{t('tasklist.processesErrorBodyLinkLabel')}
														</Link>
													</span>
												}
											/>
										) : (
											<C3EmptyState
												icon={isFiltered ? undefined : {path: EmptyMessageImage, altText: ''}}
												heading={
													isFiltered
														? t('tasklist.processesProcessNotFoundError')
														: t('tasklist.processesProcessNotPublishedError')
												}
												description={
													<span>
														{t('tasklist.processesErrorBody')}
														<Link
															href="https://docs.camunda.io/docs/components/modeler/web-modeler/run-or-publish-your-process/#publishing-a-process"
															target="_blank"
															rel="noopener noreferrer"
															inline
														>
															{t('tasklist.processesErrorBodyLinkLabel')}
														</Link>
													</span>
												}
											/>
										)}
									</Layer>
								) : (
									<>
										{/* DS-only: a plain CSS grid replacing Carbon's Grid/Column, whose compat
										    adapter is a passthrough. Carbon's sm=4/md=4/lg=5 spans out of 4/8/16
										    tracks worked out to 1 / 2 / 3 tiles per row, which is what
										    `.processGridDS` reproduces at Carbon's own breakpoints. */}
										{featureFlags.dsTasklistUI ? (
											<Layer className={styles.processGridDS}>
												{processes.map((process) => (
													<ProcessTile
														key={process.processDefinitionKey}
														process={process}
														status={selectedProcessDefinitionKey === process.processDefinitionKey ? status : 'inactive'}
														isStartButtonDisabled={isBusy}
														onStartProcess={() => handleStartProcess(process)}
													/>
												))}
											</Layer>
										) : (
											<Grid narrow as={Layer}>
												{processes.map((process) => (
													<Column
														className={styles.processTileWrapper}
														sm={4}
														md={4}
														lg={5}
														key={process.processDefinitionKey}
													>
														<ProcessTile
															process={process}
															status={
																selectedProcessDefinitionKey === process.processDefinitionKey ? status : 'inactive'
															}
															isStartButtonDisabled={isBusy}
															onStartProcess={() => handleStartProcess(process)}
														/>
													</Column>
												))}
											</Grid>
										)}
									</>
								)}
								{hasNextPage && processes.length > 0 ? (
									<Button
										onClick={onLoadMore}
										disabled={isFetchingNextPage}
										kind="ghost"
										className={styles.loadMoreButton}
									>
										{isFetchingNextPage ? t('tasklist.processesLoadingMore') : t('tasklist.processesLoadMore')}
									</Button>
								) : null}
							</div>
						</section>
					</Stack>
				</div>
			</main>
			<FirstTimeProcessWarning>{children}</FirstTimeProcessWarning>
		</>
	);
};

export {TasklistProcessesPage};
