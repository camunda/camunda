/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Heading, PageLayout, Tabs, TabsContent, TabsList, TabsTrigger, Text} from '@camunda/design-system';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/shadcn.components/ResizablePanel';
import {PROCESS_INSTANCE_TABS, type ProcessInstanceTab} from './processInstanceTabs';
import {ProcessInstanceTabPlaceholder} from './ProcessInstanceTabPlaceholder';

type Props = {
	activeTab: ProcessInstanceTab;
	onTabChange?: (tab: ProcessInstanceTab) => void;
	children?: React.ReactNode;
};

const ProcessInstance: React.FC<Props> = ({activeTab, onTabChange, children}) => {
	const {t} = useTranslation();
	const containerRef = useRef<HTMLDivElement>(null);
	const [clientHeight, setClientHeight] = useState(0);
	const panelMinHeight = clientHeight / 4;

	useEffect(() => {
		setClientHeight(containerRef.current?.clientHeight ?? 0);
	}, []);

	return (
		<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
			<Heading as="h1" className="sr-only">
				{t('operate.processInstance.title')}
			</Heading>
			<div className="flex min-h-0 flex-1 flex-col">
				<header className="border-b border-border">
					<Text as="p" className="p-4 text-sm text-neutral-foreground-subtle">
						{t('operate.processInstance.scaffold.headerPlaceholder')}
					</Text>
				</header>
				<div className="flex min-h-0 flex-1 overflow-hidden">
					<div ref={containerRef} className="min-h-0 min-w-0 flex-1 overflow-hidden">
						<ResizablePanel
							panelId="process-instance-vertical-panel"
							direction={SplitDirection.Vertical}
							minHeights={[panelMinHeight, panelMinHeight]}
						>
							<section className="flex h-full flex-col">
								<PanelHeader title={t('operate.processes.diagramHeader.title')} className="bg-transparent" />
								<Text as="p" className="p-4 text-sm text-neutral-foreground-subtle">
									{t('operate.processInstance.scaffold.diagramPanelPlaceholder')}
								</Text>
							</section>
							<section className="flex h-full flex-col overflow-hidden">
								<Tabs
									value={activeTab}
									onValueChange={(tab) => onTabChange?.(tab as ProcessInstanceTab)}
									className="flex min-h-0 grow flex-col gap-0"
								>
									<div className="flex h-12 min-h-12 items-center border-b border-border px-4">
										<TabsList aria-label={t('operate.processInstance.history.tabs')}>
											{PROCESS_INSTANCE_TABS.map((tab) => (
												<TabsTrigger key={tab} value={tab}>
													{t(`operate.processInstance.history.${tab}`)}
												</TabsTrigger>
											))}
										</TabsList>
									</div>
									{PROCESS_INSTANCE_TABS.map((tab) => (
										<TabsContent
											key={tab}
											value={tab}
											forceMount
											className="mt-0 min-h-0 flex-1 overflow-auto data-[state=inactive]:hidden"
										>
											{tab === activeTab ? (children ?? <ProcessInstanceTabPlaceholder />) : null}
										</TabsContent>
									))}
								</Tabs>
							</section>
						</ResizablePanel>
					</div>
				</div>
			</div>
		</PageLayout>
	);
};

export {ProcessInstance};
