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

type Props = {
	id: string;
	name: string;
	labelText: string;
	value?: string;
	onChange?: (value: string) => void;
	onBlur?: React.FocusEventHandler<HTMLButtonElement>;
	tenantId?: string;
	className?: string;
	disabled?: boolean;
};

const ProcessesSelect: React.FC<Props> = ({
	id,
	name,
	labelText,
	value,
	onChange,
	onBlur,
	tenantId,
	className,
	disabled,
}) => {
	const {t} = useTranslation();
	const {data: processes} = useSuspenseQuery({
		...queries.queryProcessDefinitions({
			filter: {isLatestVersion: true, ...(tenantId === undefined ? {} : {tenantId})},
			page: {limit: 1000},
		}),
		select({items}) {
			return items.map(({processDefinitionKey, name, processDefinitionId, version}) => ({
				value: processDefinitionKey,
				label: `${name ?? processDefinitionId} - v${version}`,
			}));
		},
	});

	return (
		<div className={className}>
			<Label htmlFor={id}>{labelText}</Label>
			<Select
				name={name}
				defaultValue="all"
				value={value === '' ? 'all' : value}
				onValueChange={(value) => onChange?.(value)}
				disabled={disabled}
			>
				<SelectTrigger id={id} className="mt-1.5 w-full" onBlur={onBlur}>
					<SelectValue />
				</SelectTrigger>
				<SelectContent>
					<SelectItem value="all">{t('tasklist.customFiltersModalAllProcesses')}</SelectItem>
					{processes.map(({value, label}) => (
						<SelectItem key={value} value={value}>
							{label}
						</SelectItem>
					))}
				</SelectContent>
			</Select>
		</div>
	);
};

export {ProcessesSelect};
