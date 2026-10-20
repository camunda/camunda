/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Heading, PageLayout, Separator, Text} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import {FiltersPanel} from '#/operate/shared/FiltersPanel/shadcn.components/FiltersPanel';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/shadcn.components/ResizablePanel';

const Decisions: React.FC = () => {
	const {t} = useTranslation();
	const containerRef = useRef<HTMLDivElement>(null);
	const [clientHeight, setClientHeight] = useState(0);
	const panelClassName = cn('h-full overflow-auto bg-neutral-background p-4');
	const panelMinHeight = clientHeight / 4;

	useEffect(() => {
		setClientHeight(containerRef.current?.clientHeight ?? 0);
	}, []);

	return (
		<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
			<div className="flex h-full min-h-0 flex-1 overflow-hidden">
				<Heading as="h1" className="sr-only">
					{t('operate.decisions.title')}
				</Heading>
				<FiltersPanel localStorageKey="isDecisionsFiltersCollapsed" isResetButtonDisabled>
					<Text as="p" className="text-sm text-neutral-foreground-subtle">
						{t('operate.decisions.scaffold.filtersPanelPlaceholder')}
					</Text>
				</FiltersPanel>
				<div ref={containerRef} className="min-h-0 min-w-0 flex-1 overflow-hidden">
					<ResizablePanel
						panelId="decisions-instances-vertical-panel"
						direction={SplitDirection.Vertical}
						minHeights={[panelMinHeight, panelMinHeight]}
					>
						<div className={panelClassName}>
							<Heading as="h2" variant="heading-sm">
								{t('operate.decisions.diagramHeader.title')}
							</Heading>
							<Separator className="my-3" />
							<Text as="p" className="text-sm text-neutral-foreground-subtle">
								{t('operate.decisions.scaffold.decisionPanelPlaceholder')}
							</Text>
						</div>
						<div className={panelClassName}>
							<Heading as="h2" variant="heading-sm">
								{t('operate.decisions.instancesTable.title')}
							</Heading>
							<Separator className="my-3" />
							<Text as="p" className="text-sm text-neutral-foreground-subtle">
								{t('operate.decisions.scaffold.instancesTablePlaceholder')}
							</Text>
						</div>
					</ResizablePanel>
				</div>
			</div>
		</PageLayout>
	);
};

export {Decisions};
