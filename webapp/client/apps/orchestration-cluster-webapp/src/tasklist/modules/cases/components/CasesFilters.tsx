/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Label, SearchInput} from '@camunda/design-system';
import {useNavigate} from '@tanstack/react-router';
import {useEffect, useEffectEvent, useReducer} from 'react';
import {Field, Form, type FormRenderProps} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {casesSearchDefaults} from '#/tasklist/modules/cases/searchSchema';

const SUBMIT_DEBOUNCE = 500;

type FilterValues = {
	search: string;
};

type Props = {
	initialFilterValues: Partial<FilterValues>;
};

type FieldsProps = {
	handleSubmit: FormRenderProps<FilterValues>['handleSubmit'];
};

function useDebounce(callback: () => void, delay: number) {
	const onDebounce = useEffectEvent(callback);
	const [invocationCount, invoke] = useReducer((count: number) => count + 1, 0);

	useEffect(() => {
		if (invocationCount === 0) {
			return;
		}

		const timeoutId = setTimeout(() => onDebounce(), delay);

		return () => clearTimeout(timeoutId);
	}, [delay, invocationCount]);

	return invoke;
}

const Fields: React.FC<FieldsProps> = ({handleSubmit}) => {
	const {t} = useTranslation();
	const debouncedHandleSubmit = useDebounce(handleSubmit, SUBMIT_DEBOUNCE);

	return (
		<div className="flex flex-wrap items-center gap-3">
			<Label htmlFor="cases-search" className="sr-only">
				{t('tasklist.casesSearchPlaceholder')}
			</Label>
			<Field<string> name="search">
				{({input}) => (
					<SearchInput
						id="cases-search"
						className="min-w-48 max-w-sm flex-1"
						placeholder={t('tasklist.casesSearchPlaceholder')}
						clearLabel={t('tasklist.casesClearSearch')}
						value={input.value}
						onChange={(event) => {
							input.onChange(event);
							debouncedHandleSubmit();
						}}
					/>
				)}
			</Field>
		</div>
	);
};

const CasesFilters: React.FC<Props> = ({initialFilterValues}) => {
	const navigate = useNavigate();

	return (
		<Form<FilterValues>
			initialValues={{search: initialFilterValues.search ?? ''}}
			onSubmit={({search}) =>
				navigate({
					to: '.',
					search: (previousSearch) => ({
						...previousSearch,
						search: search || undefined,
						page: casesSearchDefaults.page,
					}),
				})
			}
		>
			{({handleSubmit}) => (
				<form onSubmit={handleSubmit}>
					<Fields handleSubmit={handleSubmit} />
				</form>
			)}
		</Form>
	);
};

export {CasesFilters};
