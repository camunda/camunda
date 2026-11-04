/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Label, PageHeader, PageLayout, SearchInput} from '@camunda/design-system';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {useDebouncedUrlFilter} from '#/shared/hooks/useDebouncedUrlFilter';
import {CasesTable} from '#/tasklist/modules/cases/components/CasesTable';
import {casesSearchDefaults, type CasesSearch} from '#/tasklist/modules/cases/searchSchema';

type Props = {
	cases: ProcessInstance[];
	totalItems: number;
	search: CasesSearch;
	onSearchChange: (next: Partial<CasesSearch>) => void;
};

const TasklistCasesPage: React.FC<Props> = ({cases, totalItems, search, onSearchChange}) => {
	const {t} = useTranslation();
	const [searchDraft, setSearchDraft] = useDebouncedUrlFilter(search.search ?? '', (searchTerm) =>
		onSearchChange({search: searchTerm, page: casesSearchDefaults.page}),
	);

	return (
		<PageLayout>
			<div className="flex flex-col gap-6">
				<PageHeader
					title={t('tasklist.casesTitle')}
					description={t('tasklist.casesOpenCount', {count: totalItems})}
					actions={
						<>
							<Label htmlFor="cases-search" className="sr-only">
								{t('tasklist.casesSearchLabel')}
							</Label>
							<SearchInput
								id="cases-search"
								className="w-full sm:w-80"
								placeholder={t('tasklist.casesSearchLabel')}
								clearLabel={t('tasklist.casesClearSearch')}
								value={searchDraft}
								onChange={(event) => setSearchDraft(event.target.value)}
								onClear={() => setSearchDraft('')}
							/>
						</>
					}
				/>
				<CasesTable cases={cases} totalItems={totalItems} search={search} onSearchChange={onSearchChange} />
			</div>
		</PageLayout>
	);
};

export {TasklistCasesPage};
