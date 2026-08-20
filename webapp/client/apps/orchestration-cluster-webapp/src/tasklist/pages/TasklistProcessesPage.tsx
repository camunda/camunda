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
import {Button, Column, Grid, Layer, Link, Stack} from '@carbon/react';
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
			<main id="main-content" className={`cds--content ${styles.page}`}>
				<div className={styles.scrollContainer}>
					<Stack gap={2}>
						<section className={styles.header} aria-labelledby="processes-heading">
							<Stack className={styles.headerContent} gap={6}>
								<Grid narrow>
									<Column sm={4} md={8} lg={16}>
										<Stack gap={4}>
											<h1 id="processes-heading">{t('tasklist.headerNavItemProcesses')}</h1>
											<p>{t('tasklist.processesSubtitle')}</p>
										</Stack>
									</Column>
								</Grid>

								<ProcessesFilters initialFilterValues={initialFilterValues} tenants={tenants} />
							</Stack>
						</section>

						<section className={styles.processes}>
							<div className={styles.processesContent}>
								{processes.length === 0 ? (
									<Layer>
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
													status={selectedProcessDefinitionKey === process.processDefinitionKey ? status : 'inactive'}
													isStartButtonDisabled={isBusy}
													onStartProcess={() => handleStartProcess(process)}
												/>
											</Column>
										))}
									</Grid>
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
