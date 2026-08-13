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

	const emptyStateDescription = (
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
	);
	const emptyStateHeading = isFiltered
		? t('tasklist.processesProcessNotFoundError')
		: t('tasklist.processesProcessNotPublishedError');
	const loadMoreButton =
		hasNextPage && processes.length > 0 ? (
			<Button onClick={onLoadMore} disabled={isFetchingNextPage} kind="ghost" className={styles.loadMoreButton}>
				{isFetchingNextPage ? t('tasklist.processesLoadingMore') : t('tasklist.processesLoadMore')}
			</Button>
		) : null;

	// The DS path follows the DS PageLayout (src/components/ui/page-layout.tsx) and
	// the ProjectDetailPage example rather than Carbon's two-section page: one
	// padded, width-constrained container holds the header, then a single content
	// block offset by 24px whose children — the filter row and the tile grid — are
	// 16px apart. Carbon's version pads the header and the tile section separately,
	// which stacked two 24px paddings around the gap between them.
	if (featureFlags.dsTasklistUI) {
		return (
			<>
				<main id="main-content" className={cn('cds--content', styles.page, styles.pageDS)}>
					<div className={styles.scrollContainer}>
						<div className={styles.contentDS}>
							<header className={styles.pageHeaderDS}>
								<h1 id="processes-heading" className={styles.pageTitleDS}>
									{t('tasklist.headerNavItemProcesses')}
								</h1>
								<p className={styles.pageDescriptionDS}>{t('tasklist.processesSubtitle')}</p>
							</header>

							<div className={styles.pageBodyDS}>
								<ProcessesFilters initialFilterValues={initialFilterValues} tenants={tenants} />

								{processes.length === 0 ? (
									<Layer>
										{/* The DS EmptyState, matching NoTasks.tsx and the variables panel.
										    The illustration is dropped here — EmptyState takes a node `icon`,
										    not C3's `{path, altText}` image descriptor. */}
										<EmptyState heading={emptyStateHeading} description={emptyStateDescription} />
									</Layer>
								) : (
									// A plain CSS grid replacing Carbon's Grid/Column, whose compat adapter is
									// a passthrough. Carbon's sm=4/md=4/lg=5 spans out of 4/8/16 tracks worked
									// out to 1 / 2 / 3 tiles per row, which `.processGridDS` reproduces at
									// Carbon's own breakpoints.
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
								)}

								{loadMoreButton}
							</div>
						</div>
					</div>
				</main>
				<FirstTimeProcessWarning>{children}</FirstTimeProcessWarning>
			</>
		);
	}

	return (
		<>
			<main id="main-content" className={cn('cds--content', styles.page)}>
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
											heading={emptyStateHeading}
											description={emptyStateDescription}
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
								{loadMoreButton}
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
