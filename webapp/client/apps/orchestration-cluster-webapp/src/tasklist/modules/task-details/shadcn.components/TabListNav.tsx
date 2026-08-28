/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tabs, TabsContent, TabsList, TabsTrigger} from '@camunda/design-system';
import {useNavigate} from '@tanstack/react-router';
import {useCallback} from 'react';

type TabItem = {
	key: string;
	title: string;
	label: string;
	selected: boolean;
	to:
		'/shadcn/tasklist/$userTaskKey' | '/shadcn/tasklist/$userTaskKey/process' | '/shadcn/tasklist/$userTaskKey/history';
};

type Props = {
	label: string;
	items: TabItem[];
	userTaskKey: string;
	children?: React.ReactNode;
};

const TabListNav: React.FC<Props> = ({label, items, userTaskKey, children}) => {
	const navigate = useNavigate();
	const selectedItem = items.find(({selected}) => selected) ?? items[0];
	const handleValueChange = useCallback(
		(key: string) => {
			const nextItem = items.find((item) => item.key === key);

			if (nextItem) {
				navigate({to: nextItem.to, params: {userTaskKey}});
			}
		},
		[items, navigate, userTaskKey],
	);

	return (
		<Tabs className="flex min-h-0 w-full flex-1 flex-col" value={selectedItem?.key} onValueChange={handleValueChange}>
			<nav className="w-full self-start pl-4" aria-label={label}>
				<TabsList aria-label={label}>
					{items.map(({key, title, label: itemLabel}) => (
						<TabsTrigger key={key} value={key} aria-label={itemLabel}>
							{title}
						</TabsTrigger>
					))}
				</TabsList>
			</nav>
			{items.map(({key}) => (
				<TabsContent key={key} value={key} forceMount className="min-h-0 w-full flex-1 data-[state=inactive]:hidden">
					{key === selectedItem?.key ? children : null}
				</TabsContent>
			))}
		</Tabs>
	);
};

export {TabListNav};
export type {TabItem};
