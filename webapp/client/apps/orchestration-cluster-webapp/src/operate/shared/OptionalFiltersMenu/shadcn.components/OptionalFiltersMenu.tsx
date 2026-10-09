/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, type ReactElement} from 'react';
import {useTranslation} from 'react-i18next';
import {
	Button,
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuTrigger,
	Tooltip,
	TooltipContent,
	TooltipTrigger,
} from '@camunda/design-system';
import {Plus} from '@camunda/design-system/icons';

interface Props<T> {
	visibleFilters: T[];
	optionalFilters: {id: T; label: string}[];
	onFilterSelect: (filter: T) => void;
}

const OptionalFiltersMenu = <T extends string>({
	visibleFilters,
	optionalFilters,
	onFilterSelect,
}: Props<T>): ReactElement | null => {
	const {t} = useTranslation();
	const isFilterSelectedRef = useRef(false);
	const triggerRef = useRef<HTMLButtonElement>(null);
	const unselectedOptionalFilters = optionalFilters.filter((filter) => !visibleFilters.includes(filter.id));

	const label = t('operate.shared.optionalFiltersMenu.moreFilters');

	if (unselectedOptionalFilters.length === 0) {
		return (
			<Tooltip>
				<TooltipTrigger asChild>
					<span className="inline-block self-start">
						<Button variant="secondary" size="sm" disabled>
							<Plus aria-hidden data-icon="inline-start" />
							{label}
						</Button>
					</span>
				</TooltipTrigger>
				<TooltipContent>{t('operate.shared.optionalFiltersMenu.allFiltersAdded')}</TooltipContent>
			</Tooltip>
		);
	}

	return (
		<DropdownMenu>
			<DropdownMenuTrigger asChild>
				<Button ref={triggerRef} variant="secondary" size="sm" className="self-start">
					<Plus aria-hidden data-icon="inline-start" />
					{label}
				</Button>
			</DropdownMenuTrigger>
			<DropdownMenuContent
				align="start"
				onCloseAutoFocus={(event) => {
					// Only a selection moves focus to the new field; dismissing keeps the default trigger refocus
					if (isFilterSelectedRef.current) {
						event.preventDefault();
						isFilterSelectedRef.current = false;
					}
				}}
			>
				{unselectedOptionalFilters.map(({id, label}) => (
					<DropdownMenuItem
						key={id}
						onSelect={() => {
							isFilterSelectedRef.current = true;
							onFilterSelect(id);
							// Skip the trigger refocus on close and wait for the new field to mount
							requestAnimationFrame(() => {
								requestAnimationFrame(() => (document.getElementById(id) ?? triggerRef.current)?.focus());
							});
						}}
					>
						{label}
					</DropdownMenuItem>
				))}
			</DropdownMenuContent>
		</DropdownMenu>
	);
};

export {OptionalFiltersMenu};
