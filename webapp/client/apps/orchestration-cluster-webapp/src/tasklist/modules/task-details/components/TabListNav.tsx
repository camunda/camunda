/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useNavigate} from '@tanstack/react-router';
import {Tab, TabList, Tabs} from '#/shared/design-system-compat';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
import layoutStyles from './taskDetailsLayoutCommon.module.scss';

type TabItem = {
	key: string;
	title: string;
	label: string;
	selected: boolean;
	to: string;
	visible?: boolean;
};

type Props = {
	className?: string;
	label: string;
	items: TabItem[];
};

// These are route-driven tabs (each one navigates to a different task-details
// page), not content-switching tabs — Tabs/TabList/Tab's selectedIndex/onChange
// API is only used to drive navigation; there's no TabPanel/TabPanels here,
// selection state comes from the current route match via `selected`, not from
// Tabs' own internal state.
const TabListNav: React.FC<Props> = ({className, label, items}) => {
	const navigate = useNavigate();
	const visibleItems = items.filter(({visible}) => visible !== false);
	const selectedIndex = visibleItems.findIndex((item) => item.selected);

	return (
		<Tabs
			selectedIndex={selectedIndex === -1 ? 0 : selectedIndex}
			onChange={({selectedIndex: nextIndex}) => {
				const next = visibleItems[nextIndex];
				if (next) {
					navigate({to: next.to});
				}
			}}
		>
			{/* layoutStyles.tabs (a simple border-top+bottom) only applies with the
			    flag off: Carbon's real TabList is naturally full-width, so that
			    border already looks like a correct row divider there — it's been
			    unconditional since before this migration touched this file, and
			    removing it entirely (instead of just not adding it for the DS path)
			    silently deleted old-UI's tab divider. With the flag on, this needs
			    no className at all — taskDetailsLayoutCommon.module.scss's
			    [data-slot='tabs'] rule handles the DS-specific border/alignment
			    fix on the outer wrapper instead, since DS's TabsList (the pill) is
			    w-fit, not full-width like Carbon's. */}
			<TabList aria-label={label} className={cn(className, !featureFlags.dsTasklistUI && layoutStyles.tabs)}>
				{visibleItems.map(({key, title, label: itemLabel}) => (
					<Tab key={key} title={itemLabel}>
						{title}
					</Tab>
				))}
			</TabList>
		</Tabs>
	);
};

export {TabListNav};
export type {TabItem};
