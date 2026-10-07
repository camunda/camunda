/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {keepPreviousData, useQuery} from '@tanstack/react-query';
import {Combobox, Input, Label, type ComboboxOption} from '@camunda/design-system';
import type {OwnerType} from '@camunda/camunda-api-zod-schemas/8.11';
import {queries} from '#/shared/http/queries';

const LOOKUP_DEBOUNCE = 300;
const LOOKUP_LIMIT = 50;

type OwnerSelectionProps = {
	ownerType: OwnerType;
	value: string;
	onChange: (ownerId: string) => void;
	onBlur: () => void;
	error?: string;
	isOidc: boolean;
	isCamundaGroupsEnabled: boolean;
};

type NamedEntity = {id: string; name?: string | null};

function toOptions(entities: NamedEntity[]): ComboboxOption[] {
	return entities.map(({id, name}) => ({value: id, label: name && name !== id ? `${name} (${id})` : id}));
}

function toLikeFilter(term: string) {
	return term === '' ? undefined : {$like: `*${term}*`};
}

function useOwnerOptions(ownerType: OwnerType, term: string): ComboboxOption[] {
	const like = toLikeFilter(term);
	const page = {from: 0, limit: LOOKUP_LIMIT};

	const users = useQuery({
		...queries.queryUsers({filter: like ? {username: like} : {}, page}),
		enabled: ownerType === 'USER',
		placeholderData: keepPreviousData,
		select: ({items}) => toOptions(items.map(({username, name}) => ({id: username, name}))),
	});
	const groups = useQuery({
		...queries.queryGroups({filter: like ? {groupId: like} : {}, page}),
		enabled: ownerType === 'GROUP',
		placeholderData: keepPreviousData,
		select: ({items}) => toOptions(items.map(({groupId, name}) => ({id: groupId, name}))),
	});
	const roles = useQuery({
		...queries.queryRoles({filter: like ? {roleId: like} : {}, page}),
		enabled: ownerType === 'ROLE',
		placeholderData: keepPreviousData,
		select: ({items}) => toOptions(items.map(({roleId, name}) => ({id: roleId, name}))),
	});
	const mappingRules = useQuery({
		...queries.queryMappingRules({filter: like ? {mappingRuleId: like} : undefined, page}),
		enabled: ownerType === 'MAPPING_RULE',
		placeholderData: keepPreviousData,
		select: ({items}) => toOptions(items.map(({mappingRuleId, name}) => ({id: mappingRuleId, name}))),
	});

	switch (ownerType) {
		case 'USER':
			return users.data ?? [];
		case 'GROUP':
			return groups.data ?? [];
		case 'ROLE':
			return roles.data ?? [];
		case 'MAPPING_RULE':
			return mappingRules.data ?? [];
		default:
			return [];
	}
}

const EntityCombobox: React.FC<OwnerSelectionProps> = ({ownerType, value, onChange, onBlur, error}) => {
	const {t} = useTranslation();
	const [inputValue, setInputValue] = useState('');
	const [lookupTerm, setLookupTerm] = useState('');
	const [selected, setSelected] = useState<ComboboxOption | null>(null);

	useEffect(() => {
		const timeoutId = setTimeout(() => setLookupTerm(inputValue.trim()), LOOKUP_DEBOUNCE);
		return () => clearTimeout(timeoutId);
	}, [inputValue]);

	const fetched = useOwnerOptions(ownerType, lookupTerm);
	// A selected owner can drop out of the fetched page once the user types a new search term.
	const options =
		selected !== null && value === selected.value && !fetched.some((option) => option.value === selected.value)
			? [selected, ...fetched]
			: fetched;

	return (
		<div className="flex flex-col gap-1.5" onBlur={onBlur}>
			<Label htmlFor="authorization-owner">{t('admin.authorizations.ownerFieldLabel')}</Label>
			<Combobox
				id="authorization-owner"
				className="w-full"
				options={options}
				value={value === '' ? undefined : value}
				externalFiltering
				placeholder={t('admin.authorizations.ownerFieldPlaceholder')}
				searchPlaceholder={t('admin.authorizations.searchByOwnerId')}
				emptyIndicator={t('admin.authorizations.noOwnersFound')}
				aria-invalid={Boolean(error)}
				onInputChange={setInputValue}
				onValueChange={(next) => {
					setSelected(options.find((option) => option.value === next) ?? null);
					onChange(next ?? '');
				}}
			/>
			{error ? (
				<span role="alert" className="text-xs text-danger-foreground-subtle">
					{error}
				</span>
			) : null}
		</div>
	);
};

type TextOwnerFieldProps = {
	label: string;
	placeholder: string;
	value: string;
	onChange: (ownerId: string) => void;
	onBlur: () => void;
	error?: string;
};

const TextOwnerField: React.FC<TextOwnerFieldProps> = ({label, placeholder, value, onChange, onBlur, error}) => (
	<div className="flex flex-col gap-1.5">
		<Label htmlFor="authorization-owner">{label}</Label>
		<Input
			id="authorization-owner"
			placeholder={placeholder}
			value={value}
			onChange={(event) => onChange(event.target.value)}
			onBlur={onBlur}
			aria-invalid={Boolean(error)}
			invalidText={error}
		/>
	</div>
);

const OwnerSelection: React.FC<OwnerSelectionProps> = (props) => {
	const {t} = useTranslation();
	const {ownerType, value, onChange, onBlur, error, isOidc, isCamundaGroupsEnabled} = props;
	const textFieldProps = {value, onChange, onBlur, error};

	switch (ownerType) {
		case 'USER':
			return isOidc ? (
				<TextOwnerField
					{...textFieldProps}
					label={t('admin.authorizations.usernameFieldLabel')}
					placeholder={t('admin.authorizations.usernameFieldPlaceholder')}
				/>
			) : (
				<EntityCombobox {...props} />
			);
		case 'GROUP':
			return isCamundaGroupsEnabled ? (
				<EntityCombobox {...props} />
			) : (
				<TextOwnerField
					{...textFieldProps}
					label={t('admin.authorizations.groupIdFieldLabel')}
					placeholder={t('admin.authorizations.groupIdFieldPlaceholder')}
				/>
			);
		case 'ROLE':
		case 'MAPPING_RULE':
			return <EntityCombobox {...props} />;
		case 'CLIENT':
			return (
				<TextOwnerField
					{...textFieldProps}
					label={t('admin.authorizations.clientIdFieldLabel')}
					placeholder={t('admin.authorizations.clientIdFieldPlaceholder')}
				/>
			);
		default:
			return null;
	}
};

export {OwnerSelection};
