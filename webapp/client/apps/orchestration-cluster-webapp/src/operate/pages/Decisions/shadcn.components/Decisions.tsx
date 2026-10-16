/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {Form} from 'react-final-form';
import {Heading, PageLayout} from '@camunda/design-system';
import {FilterSidebar} from '#/operate/shared/FilterSidebar/shadcn.components/FilterSidebar';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/shadcn.components/ResizablePanel';
import {AutoSubmit} from '#/operate/shared/AutoSubmit/AutoSubmit';
import type {DecisionsSearch} from '../decisionsFilter';
import type {OptionalFilter, OptionalFilterValues} from '../optionalFilters';
import {OptionalFiltersFormGroup} from './OptionalFiltersFormGroup';
import {DecisionFilters} from './DecisionFilters';
import {DecisionPanel} from './DecisionPanel';
import {InstancesTable} from './InstancesTable';
import {DecisionOperations} from '../DecisionOperations/shadcn.components/DecisionOperations';
import {useDecisionDefinitionSelection} from './useDecisionDefinitionSelection';

type FilterFormValues = OptionalFilterValues & {tenantId?: string};

type Props = {
	search: DecisionsSearch;
};

const Decisions: React.FC<Props> = ({search}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);
	const selection = useDecisionDefinitionSelection({
		decisionDefinitionId: search.decisionDefinitionId,
		decisionDefinitionVersion: search.decisionDefinitionVersion,
		tenantId: search.tenantId,
	});
	const {decisionEvaluationInstanceKey, processInstanceKey, businessId, evaluationDateFrom, evaluationDateTo} = search;
	const optionalFilterValues = useMemo<OptionalFilterValues>(
		() => ({decisionEvaluationInstanceKey, processInstanceKey, businessId, evaluationDateFrom, evaluationDateTo}),
		[decisionEvaluationInstanceKey, processInstanceKey, businessId, evaluationDateFrom, evaluationDateTo],
	);
	const filterFormValues = useMemo<FilterFormValues>(
		() => ({tenantId: search.tenantId, ...optionalFilterValues}),
		[search.tenantId, optionalFilterValues],
	);
	const isResetDisabled =
		search.evaluated &&
		search.failed &&
		!search.decisionDefinitionId &&
		search.decisionDefinitionVersion === undefined &&
		search.tenantId === undefined &&
		search.sort === undefined &&
		Object.values(optionalFilterValues).every((value) => value === undefined) &&
		visibleFilters.length === 0;
	const containerRef = useRef<HTMLDivElement>(null);
	const [clientHeight, setClientHeight] = useState(0);
	const panelMinHeight = clientHeight / 4;

	useEffect(() => {
		setClientHeight(containerRef.current?.clientHeight ?? 0);
	}, []);

	return (
		<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
			<div className="flex h-full min-h-0 flex-1 overflow-hidden">
				<Heading as="h1" className="sr-only">
					{t('operate.decisions.title')}
				</Heading>
				<Form<FilterFormValues>
					onSubmit={(values) => {
						void navigate({
							to: '.',
							search: (prev) => ({
								...prev,
								...((values.tenantId || undefined) !== prev.tenantId
									? {decisionDefinitionId: undefined, decisionDefinitionVersion: undefined}
									: {}),
								tenantId: values.tenantId || undefined,
								decisionEvaluationInstanceKey: values.decisionEvaluationInstanceKey || undefined,
								processInstanceKey: values.processInstanceKey || undefined,
								businessId: values.businessId || undefined,
								evaluationDateFrom: values.evaluationDateFrom || undefined,
								evaluationDateTo: values.evaluationDateTo || undefined,
							}),
						});
					}}
					initialValues={filterFormValues}
				>
					{({handleSubmit, form}) => (
						<form onSubmit={handleSubmit} className="flex h-full min-h-0">
							<AutoSubmit fieldsToSkipTimeout={['tenantId']} />
							<FilterSidebar
								localStorageKey="isDecisionsFiltersCollapsed"
								isResetButtonDisabled={isResetDisabled}
								onResetClick={() => {
									form.reset();
									setVisibleFilters([]);
									void navigate({to: '.', search: {evaluated: true, failed: true}});
								}}
							>
								<div className="flex flex-col gap-8">
									<DecisionFilters
										decisionDefinitionId={search.decisionDefinitionId}
										decisionDefinitionVersion={search.decisionDefinitionVersion}
										tenantId={search.tenantId}
										evaluated={search.evaluated}
										failed={search.failed}
									/>
									<OptionalFiltersFormGroup
										filters={optionalFilterValues}
										visibleFilters={visibleFilters}
										onVisibleFilterChange={setVisibleFilters}
									/>
								</div>
							</FilterSidebar>
						</form>
					)}
				</Form>
				<div ref={containerRef} className="min-h-0 min-w-0 flex-1 overflow-hidden">
					<ResizablePanel
						panelId="decisions-instances-vertical-panel"
						direction={SplitDirection.Vertical}
						minHeights={[panelMinHeight, panelMinHeight]}
					>
						<DecisionPanel
							{...selection}
							headerActions={
								selection.decisionDefinitionSelection.kind === 'single-version' ? (
									<DecisionOperations
										key={selection.decisionDefinitionSelection.definition.decisionDefinitionKey}
										definition={selection.decisionDefinitionSelection.definition}
									/>
								) : undefined
							}
						/>
						<div className="h-full overflow-hidden">
							<InstancesTable search={search} />
						</div>
					</ResizablePanel>
				</div>
			</div>
		</PageLayout>
	);
};

export {Decisions};
