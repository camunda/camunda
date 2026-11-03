/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Badge, Heading, PageLayout, Tabs, TabsContent, TabsList, TabsTrigger, Text} from '@camunda/design-system';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/shadcn.components/ResizablePanel';
import {PROCESS_INSTANCE_TABS, TAB_LABEL_KEYS, type ProcessInstanceTab} from './processInstanceTabs';
import {ProcessInstanceTabPlaceholder} from './ProcessInstanceTabPlaceholder';

type Props = {
	activeTab: ProcessInstanceTab;
	isDesktop?: boolean;
	visibleTabs: readonly ProcessInstanceTab[];
	onTabChange?: (tab: ProcessInstanceTab) => void;
	breadcrumb?: React.ReactNode;
	header: React.ReactNode;
	topPanel?: React.ReactNode;
	incidentsCount?: number;
	historyPanel?: React.ReactNode;
	children?: React.ReactNode;
};

const ProcessInstance: React.FC<Props> = ({
	activeTab,
	isDesktop = true,
	visibleTabs,
	onTabChange,
	breadcrumb,
	header,
	topPanel,
	incidentsCount,
	historyPanel,
	children,
}) => {
	const {t} = useTranslation();
	const containerRef = useRef<HTMLDivElement>(null);
	const [clientHeight, setClientHeight] = useState(0);
	const panelMinHeight = clientHeight / 4;
	const [bottomWidth, setBottomWidth] = useState(0);

	useEffect(() => {
		const container = containerRef.current;
		setClientHeight(container?.clientHeight ?? 0);
		setBottomWidth(container?.clientWidth ?? 0);
		if (!container) {
			return;
		}
		const observer = new ResizeObserver(() => {
			setBottomWidth((current) => (current === container.clientWidth ? current : container.clientWidth));
		});
		observer.observe(container);
		return () => observer.disconnect();
	}, []);

	const tabsSection = (
		<section className="flex h-full flex-col overflow-hidden">
			<Tabs
				value={activeTab}
				onValueChange={(tab) => onTabChange?.(tab as ProcessInstanceTab)}
				className="flex min-h-0 grow flex-col gap-0"
			>
				<div className="flex h-12 min-h-12 items-center px-4">
					<TabsList variant="line" aria-label={t('operate.processInstance.history.tabs')}>
						{visibleTabs.map((tab) => (
							<TabsTrigger key={tab} value={tab}>
								{t(TAB_LABEL_KEYS[tab])}
								{tab === 'incidents' && incidentsCount !== undefined && incidentsCount > 0 && (
									<Badge
										variant="danger"
										className="border-transparent bg-[var(--danger-foreground-subtle)] text-[var(--background)]"
									>
										{incidentsCount}
									</Badge>
								)}
							</TabsTrigger>
						))}
					</TabsList>
				</div>
				{PROCESS_INSTANCE_TABS.map((tab) => (
					<TabsContent
						key={tab}
						value={tab}
						forceMount
						className="mt-0 min-h-0 flex-1 overflow-auto rounded-none data-[state=inactive]:hidden"
					>
						{tab === activeTab ? (children ?? <ProcessInstanceTabPlaceholder />) : null}
					</TabsContent>
				))}
			</Tabs>
		</section>
	);
	const bottomPanel =
		historyPanel && isDesktop ? (
			<ResizablePanel
				panelId="process-instance-bottom-panel"
				direction={SplitDirection.Horizontal}
				minWidths={[Math.max(420, bottomWidth / 4), bottomWidth / 4]}
			>
				{historyPanel}
				{tabsSection}
			</ResizablePanel>
		) : (
			tabsSection
		);

	return (
		<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
			<Heading as="h1" className="sr-only">
				{t('operate.processInstance.title')}
			</Heading>
			<div className="flex min-h-0 flex-1 flex-col">
				{breadcrumb}
				{header}
				<div className="flex min-h-0 flex-1 overflow-hidden">
					<div ref={containerRef} className="min-h-0 min-w-0 flex-1 overflow-hidden">
						<ResizablePanel
							panelId="process-instance-vertical-panel"
							direction={SplitDirection.Vertical}
							minHeights={[panelMinHeight, panelMinHeight]}
						>
							<section className="flex h-full min-h-0 flex-col">
								{topPanel ?? (
									<>
										<PanelHeader title={t('operate.processes.diagramHeader.title')} className="bg-transparent" />
										<Text as="p" className="p-4 text-sm text-neutral-foreground-subtle">
											{t('operate.processInstance.scaffold.diagramPanelPlaceholder')}
										</Text>
									</>
								)}
							</section>
							{bottomPanel}
						</ResizablePanel>
					</div>
				</div>
			</div>
		</PageLayout>
	);
};

export {ProcessInstance};
