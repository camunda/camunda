/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ReactElement} from 'react';
import {useTranslation} from 'react-i18next';
import {Combobox, Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
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
	const unselectedOptionalFilters = optionalFilters.filter((filter) => !visibleFilters.includes(filter.id));

	if (unselectedOptionalFilters.length === 0) {
		return (
			<Tooltip>
				<TooltipTrigger asChild>
					<span className="relative block w-full">
						<Plus aria-hidden className="pointer-events-none absolute left-2 top-1/2 size-4 -translate-y-1/2" />
						<Combobox
							className="w-full pl-8"
							size="sm"
							options={[]}
							value=""
							aria-label={t('operate.shared.optionalFiltersMenu.moreFilters')}
							placeholder={t('operate.shared.optionalFiltersMenu.moreFilters')}
							disabled
						/>
					</span>
				</TooltipTrigger>
				<TooltipContent>{t('operate.shared.optionalFiltersMenu.allFiltersAdded')}</TooltipContent>
			</Tooltip>
		);
	}

	return (
		<div className="relative w-full">
			<Plus aria-hidden className="pointer-events-none absolute left-2 top-1/2 z-10 size-4 -translate-y-1/2" />
			<Combobox
				className="w-full pl-8"
				size="sm"
				options={unselectedOptionalFilters.map(({id, label}) => ({value: id, label}))}
				// Stays empty so the trigger keeps showing the placeholder after a filter is added
				value=""
				aria-label={t('operate.shared.optionalFiltersMenu.moreFilters')}
				placeholder={t('operate.shared.optionalFiltersMenu.moreFilters')}
				onValueChange={(value) => {
					const filter = unselectedOptionalFilters.find(({id}) => id === value)?.id;
					if (filter === undefined) {
						return;
					}
					onFilterSelect(filter);
					// The popover returns focus to its trigger on close, so wait for the new field to mount
					requestAnimationFrame(() => {
						requestAnimationFrame(() => document.getElementById(filter)?.focus());
					});
				}}
			/>
		</div>
	);
};

export {OptionalFiltersMenu};
