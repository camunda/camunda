/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {Form} from 'react-final-form';
import {Heading, PageLayout, Text} from '@camunda/design-system';
import {FilterSidebar} from '#/operate/shared/FilterSidebar/shadcn.components/FilterSidebar';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/shadcn.components/ResizablePanel';
import {AutoSubmit} from '#/operate/shared/AutoSubmit/AutoSubmit';
import type {ProcessesSearch} from '../processesFilter';
import {ProcessFilters} from './ProcessFilters';

type FilterFormValues = {tenantId?: string};

type Props = {
	search: ProcessesSearch;
};

const Processes: React.FC<Props> = ({search}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const filterFormValues = useMemo<FilterFormValues>(() => ({tenantId: search.tenantId}), [search.tenantId]);
	const isResetDisabled =
		search.active &&
		search.incidents &&
		search.suspended &&
		!search.completed &&
		!search.canceled &&
		!search.process &&
		search.version === undefined &&
		search.elementId === undefined &&
		search.tenantId === undefined;

	const containerRef = useRef<HTMLDivElement>(null);
	const [clientHeight, setClientHeight] = useState(0);
	const panelMinHeight = clientHeight / 4;

	useEffect(() => {
		setClientHeight(containerRef.current?.clientHeight ?? 0);
	}, []);

	return (
		<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
			<div className="flex h-full min-h-0 flex-1 overflow-hidden">
				<Heading as="h1" className="sr-only">
					{t('operate.processes.title')}
				</Heading>
				<Form<FilterFormValues>
					onSubmit={(values) => {
						void navigate({
							to: '.',
							search: (prev) => ({
								...prev,
								...((values.tenantId || undefined) !== prev.tenantId
									? {process: undefined, version: undefined, elementId: undefined}
									: {}),
								tenantId: values.tenantId || undefined,
							}),
						});
					}}
					initialValues={filterFormValues}
				>
					{({handleSubmit, form}) => (
						<form onSubmit={handleSubmit} className="flex h-full min-h-0">
							<AutoSubmit fieldsToSkipTimeout={['tenantId']} />
							<FilterSidebar
								hideCollapsedLabel
								localStorageKey="isProcessesFiltersCollapsed"
								isResetButtonDisabled={isResetDisabled}
								onResetClick={() => {
									form.reset();
									void navigate({to: '.', search: {active: true, suspended: true, incidents: true}});
								}}
							>
								<div className="flex flex-col gap-8">
									<ProcessFilters
										process={search.process}
										version={search.version}
										elementId={search.elementId}
										tenantId={search.tenantId}
										active={search.active}
										incidents={search.incidents}
										suspended={search.suspended}
										completed={search.completed}
										canceled={search.canceled}
									/>
								</div>
							</FilterSidebar>
						</form>
					)}
				</Form>
				<div ref={containerRef} className="min-h-0 min-w-0 flex-1 overflow-hidden">
					<ResizablePanel
						panelId="process-instances-vertical-panel"
						direction={SplitDirection.Vertical}
						minHeights={[panelMinHeight, panelMinHeight]}
					>
						<section className="flex h-full flex-col">
							<PanelHeader title={t('operate.processes.diagramHeader.title')} className="bg-transparent" />
							<Text as="p" className="p-4 text-sm text-neutral-foreground-subtle">
								{t('operate.processes.scaffold.diagramPanelPlaceholder')}
							</Text>
						</section>
						<section className="flex h-full flex-col">
							<PanelHeader title={t('operate.processes.instancesTable.title')} className="bg-transparent" />
							<Text as="p" className="p-4 text-sm text-neutral-foreground-subtle">
								{t('operate.processes.scaffold.instancesTablePlaceholder')}
							</Text>
						</section>
					</ResizablePanel>
				</div>
			</div>
		</PageLayout>
	);
};

export {Processes};
