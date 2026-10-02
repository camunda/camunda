/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Heading, PageLayout, Separator, Text} from '@camunda/design-system';
import {cn} from '#/shared/cn';

const Decisions: React.FC = () => {
	const {t} = useTranslation();
	const panelClassName = cn('rounded-md border border-neutral-border-default bg-neutral-background p-4');

	return (
		<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
			<div className="flex h-full min-h-0 flex-1 gap-4 overflow-hidden px-6 py-4">
				<h1 className="sr-only">{t('operate.decisions.title')}</h1>
				<div className={cn(panelClassName, 'w-80 shrink-0')}>
					<Heading as="h2" variant="heading-sm">
						{t('operate.decisions.filters.decisionSection')}
					</Heading>
					<Separator className="my-3" />
					<Text as="p" className="text-sm text-neutral-foreground-subtle">
						{t('operate.decisions.scaffold.filtersPanelPlaceholder')}
					</Text>
				</div>
				{/* grid-rows-2 mirrors the live page's InstancesList splitter, which starts the
				Decision and Instances panes at an even 50/50 split (with a quarter-height floor each) —
				see InstancesList.tsx. Swap this for the real splitter once it lands in the design system. */}
				<div className="grid min-h-0 flex-1 grid-rows-2 gap-4 overflow-hidden">
					<div className={cn(panelClassName, 'min-h-0')}>
						<Heading as="h2" variant="heading-sm">
							{t('operate.decisions.diagramHeader.title')}
						</Heading>
						<Separator className="my-3" />
						<Text as="p" className="text-sm text-neutral-foreground-subtle">
							{t('operate.decisions.scaffold.decisionPanelPlaceholder')}
						</Text>
					</div>
					<div className={cn(panelClassName, 'min-h-0')}>
						<Heading as="h2" variant="heading-sm">
							{t('operate.decisions.instancesTable.title')}
						</Heading>
						<Separator className="my-3" />
						<Text as="p" className="text-sm text-neutral-foreground-subtle">
							{t('operate.decisions.scaffold.instancesTablePlaceholder')}
						</Text>
					</div>
				</div>
			</div>
		</PageLayout>
	);
};

export {Decisions};
