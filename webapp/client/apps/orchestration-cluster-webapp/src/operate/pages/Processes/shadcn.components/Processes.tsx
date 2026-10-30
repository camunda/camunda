/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Heading, PageLayout, Text} from '@camunda/design-system';
import {FilterSidebar} from '#/operate/shared/FilterSidebar/shadcn.components/FilterSidebar';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/shadcn.components/ResizablePanel';

const Processes: React.FC = () => {
	const {t} = useTranslation();
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
				<FilterSidebar localStorageKey="isProcessesFiltersCollapsed" isResetButtonDisabled hideCollapsedLabel>
					<Text as="p" className="text-sm text-neutral-foreground-subtle">
						{t('operate.processes.scaffold.filtersPanelPlaceholder')}
					</Text>
				</FilterSidebar>
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
