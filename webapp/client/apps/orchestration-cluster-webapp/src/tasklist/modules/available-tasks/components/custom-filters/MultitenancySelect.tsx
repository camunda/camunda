/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Label, Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@camunda/design-system';
import {useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {queries} from '#/shared/http/queries';
import {useCallback} from 'react';

const ALL_TENANTS_SELECT_VALUE = '__all-tenants__';

type Props = {
	id: string;
	name: string;
	labelText: string;
	value?: string;
	onChange?: (value: string) => void;
	onBlur?: React.FocusEventHandler<HTMLButtonElement>;
	className?: string;
	disabled?: boolean;
};

const MultitenancySelect: React.FC<Props> = ({id, name, labelText, value, onChange, onBlur, className, disabled}) => {
	const {t} = useTranslation();
	const {data: tenants} = useSuspenseQuery({
		...queries.getCurrentUser(),
		select: ({tenants}) => tenants,
	});
	const handleChange = useCallback(
		(selectedValue: string) => {
			onChange?.(selectedValue === ALL_TENANTS_SELECT_VALUE ? '' : selectedValue);
		},
		[onChange],
	);

	return (
		<div className={className}>
			<Label htmlFor={id}>{labelText}</Label>
			<Select
				name={name}
				defaultValue={ALL_TENANTS_SELECT_VALUE}
				value={value === '' ? ALL_TENANTS_SELECT_VALUE : value}
				onValueChange={handleChange}
				disabled={disabled}
			>
				<SelectTrigger id={id} className="mt-1.5 w-full" onBlur={onBlur}>
					<SelectValue />
				</SelectTrigger>
				<SelectContent>
					<SelectItem value={ALL_TENANTS_SELECT_VALUE}>{t('tasklist.customFiltersModalAllTenants')}</SelectItem>
					{tenants.map(({tenantId, name}) => (
						<SelectItem key={tenantId} value={tenantId}>
							{name}
						</SelectItem>
					))}
				</SelectContent>
			</Select>
		</div>
	);
};

export {MultitenancySelect};
