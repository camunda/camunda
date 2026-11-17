/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useState} from 'react';
import {useNavigate} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {Button, Search} from '@carbon/react';
import {SortAscending, SortDescending} from '@carbon/react/icons';
import {useInstanceHistory} from './useInstanceHistory';
import {useProcessInstancePage} from './useProcessInstancePage';
import type {ElementStatusFilter} from './processInstanceSearch';
import {FilterSwitcher, FilterSwitcherButton, SearchControls, SearchRow} from './instanceHistory.styled';

const SEARCH_DEBOUNCE_MS = 750;
const STATUS_FILTERS: (ElementStatusFilter | undefined)[] = [undefined, 'active', 'incidents'];

function InstanceHistorySearch() {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const history = useInstanceHistory();
	const {search} = useProcessInstancePage();
	const applied = search.elementSearch ?? '';

	const updateSearch = useCallback(
		(update: {elementSearch?: string; elementStatus?: ElementStatusFilter}) =>
			void navigate({to: '.', search: (current) => ({...current, ...update}), replace: true}),
		[navigate],
	);

	const [draft, setDraft] = useState(applied);
	const [lastApplied, setLastApplied] = useState(applied);
	if (lastApplied !== applied) {
		setLastApplied(applied);
		if (draft.trim() === lastApplied) {
			setDraft(applied);
		}
	}

	useEffect(() => {
		const next = draft.trim();
		if (next === applied) {
			return;
		}
		const timeoutId = setTimeout(() => updateSearch({elementSearch: next || undefined}), SEARCH_DEBOUNCE_MS);
		return () => clearTimeout(timeoutId);
	}, [draft, applied, updateSearch]);
	const isLatestFirst = history.sortOrder === 'DESC';

	return (
		<SearchControls>
			<SearchRow>
				<Search
					size="sm"
					labelText={t('operate.processInstance.history.search.label')}
					placeholder={t('operate.processInstance.history.search.placeholder')}
					value={draft}
					onChange={(event) => setDraft(event.target.value)}
					onBlur={() => setDraft(draft.trim())}
					onClear={() => {
						setDraft('');
						updateSearch({elementSearch: undefined});
					}}
				/>
				<Button
					kind="ghost"
					size="sm"
					renderIcon={isLatestFirst ? SortDescending : SortAscending}
					onClick={() => history.changeSortOrder(isLatestFirst ? 'ASC' : 'DESC')}
				>
					{t(`operate.processInstance.history.search.${isLatestFirst ? 'latestFirst' : 'oldestFirst'}`)}
				</Button>
			</SearchRow>
			<FilterSwitcher role="group" aria-label={t('operate.processInstance.history.search.statusFilter')}>
				{STATUS_FILTERS.map((status) => (
					<FilterSwitcherButton
						key={status ?? 'all'}
						type="button"
						aria-pressed={search.elementStatus === status}
						onClick={() => updateSearch({elementStatus: status})}
					>
						{t(`operate.processInstance.history.search.status.${status ?? 'all'}`)}
					</FilterSwitcherButton>
				))}
			</FilterSwitcher>
		</SearchControls>
	);
}

export {InstanceHistorySearch};
