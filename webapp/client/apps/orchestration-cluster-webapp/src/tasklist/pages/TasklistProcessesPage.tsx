/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import EmptyMessageImage from '#/tasklist/modules/processes/empty-message-image.svg';
import {ProcessesFilters} from '#/tasklist/modules/processes/components/ProcessesFilters';
import {C3EmptyState} from '@camunda/camunda-composite-components';
import {Column, Grid, Layer, Link, Stack} from '@carbon/react';
import {useSearch} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import styles from './TasklistProcessesPage.module.scss';

const TasklistProcessesPage: React.FC = () => {
	const {t} = useTranslation();
	const initialFilterValues = useSearch({from: '/_auth/tasklist/processes'});

	return (
		<main className={`cds--content ${styles.page}`}>
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

							<ProcessesFilters initialFilterValues={initialFilterValues} />
						</Stack>
					</section>

					<section className={styles.processes}>
						<div className={styles.processesContent}>
							<Layer>
								<C3EmptyState
									icon={{path: EmptyMessageImage, altText: ''}}
									heading={t('tasklist.processesProcessNotPublishedError')}
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
						</div>
					</section>
				</Stack>
			</div>
		</main>
	);
};

export {TasklistProcessesPage};
