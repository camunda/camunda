/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {PageHeader, PageLayout} from '@camunda/design-system';
import type {ElementInstanceInspection, ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {CasesFilters} from '#/tasklist/modules/cases/components/CasesFilters';
import {CasesTable} from '#/tasklist/modules/cases/components/CasesTable';
import type {CasesSearch} from '#/tasklist/modules/cases/searchSchema';

type Props = {
	cases: ProcessInstance[];
	waitStates: ElementInstanceInspection[];
	totalItems: number;
	hasMoreTotalItems: boolean;
	search: CasesSearch;
};

const TasklistCasesPage: React.FC<Props> = ({cases, waitStates, totalItems, hasMoreTotalItems, search}) => {
	const {t} = useTranslation();

	return (
		<PageLayout>
			<div className="flex flex-col gap-6">
				<PageHeader
					title={t('tasklist.casesTitle')}
					description={
						hasMoreTotalItems
							? t('tasklist.casesOpenCountMore', {count: totalItems})
							: t('tasklist.casesOpenCount', {count: totalItems})
					}
				/>

				<div className="flex flex-col gap-4">
					<CasesFilters initialFilterValues={search} />
					<CasesTable cases={cases} waitStates={waitStates} totalItems={totalItems} search={search} />
				</div>
			</div>
		</PageLayout>
	);
};

export {TasklistCasesPage};
