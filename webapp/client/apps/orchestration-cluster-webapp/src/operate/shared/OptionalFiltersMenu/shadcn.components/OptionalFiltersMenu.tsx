/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type ReactElement} from 'react';
import {useTranslation} from 'react-i18next';
import {Button, DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger} from '@camunda/design-system';
import {Filter} from '@camunda/design-system/icons';

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
	const unselectedOptionalFilters = optionalFilters.filter((filter) => !visibleFilters.includes(filter.id));

	return unselectedOptionalFilters.length > 0 ? (
		<div className="flex justify-end">
			<DropdownMenu>
				<DropdownMenuTrigger asChild>
					<Button type="button" variant="ghost">
						<span>{t('operate.shared.optionalFiltersMenu.moreFilters')}</span>
						<Filter aria-hidden />
					</Button>
				</DropdownMenuTrigger>
				<DropdownMenuContent side="top" align="end">
					{unselectedOptionalFilters.map((filter) => (
						<DropdownMenuItem
							key={filter.id}
							onClick={() => onFilterSelect(filter.id)}
							data-testid={`optional-filter-menuitem-${filter.id}`}
						>
							{filter.label}
						</DropdownMenuItem>
					))}
				</DropdownMenuContent>
			</DropdownMenu>
		</div>
	) : null;
};

export {OptionalFiltersMenu};
