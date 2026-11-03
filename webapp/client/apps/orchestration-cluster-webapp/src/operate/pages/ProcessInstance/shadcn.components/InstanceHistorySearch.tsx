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
import {Button, SearchInput, ToggleGroup, ToggleGroupItem} from '@camunda/design-system';
import {ArrowDown, ArrowUp} from '@camunda/design-system/icons';
import {useInstanceHistory} from '../useInstanceHistory';
import {useProcessInstancePage} from '../useProcessInstancePage';
import type {ElementStatusFilter} from '../processInstanceSearch';

const SEARCH_DEBOUNCE_MS = 750;
const STATUS_FILTERS: (ElementStatusFilter | undefined)[] = [undefined, 'active', 'incidents'];

const InstanceHistorySearch: React.FC = () => {
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
	const SortIcon = isLatestFirst ? ArrowDown : ArrowUp;

	return (
		<div className="flex flex-col gap-2 border-b border-border p-2">
			<div className="flex items-center gap-2">
				<SearchInput
					size="sm"
					aria-label={t('operate.processInstance.history.search.label')}
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
					variant="ghost"
					size="sm"
					className="shrink-0"
					onClick={() => history.changeSortOrder(isLatestFirst ? 'ASC' : 'DESC')}
				>
					<SortIcon aria-hidden="true" />
					{t(`operate.processInstance.history.search.${isLatestFirst ? 'latestFirst' : 'oldestFirst'}`)}
				</Button>
			</div>
			<ToggleGroup
				type="single"
				variant="outline"
				size="sm"
				fullWidth
				aria-label={t('operate.processInstance.history.search.statusFilter')}
				value={search.elementStatus ?? 'all'}
				onValueChange={(value) => {
					if (value !== '') {
						updateSearch({elementStatus: value === 'all' ? undefined : (value as ElementStatusFilter)});
					}
				}}
			>
				{STATUS_FILTERS.map((status) => (
					<ToggleGroupItem key={status ?? 'all'} value={status ?? 'all'}>
						{t(`operate.processInstance.history.search.status.${status ?? 'all'}`)}
					</ToggleGroupItem>
				))}
			</ToggleGroup>
		</div>
	);
};

export {InstanceHistorySearch};
