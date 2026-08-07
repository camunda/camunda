/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useQuery, useSuspenseQuery} from '@tanstack/react-query';
import {useNavigate} from '@tanstack/react-router';
import {Form} from 'react-final-form';
import {Checkbox, ComboBox, Dropdown, Stack} from '@carbon/react';
import {decisionDefinitionSelectionOptions, decisionDefinitionsOptions} from './decisions.queries';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {InstancesList} from '#/operate/shared/InstancesList/InstancesList';
import {FiltersPanel} from '#/operate/shared/FiltersPanel/FiltersPanel';
import {Title, Form as StyledForm} from '#/operate/shared/FiltersPanel/styled';
import {AutoSubmit} from '#/operate/shared/AutoSubmit/AutoSubmit';
import {TenantField} from '#/operate/shared/TenantField/TenantField';
import {WarningFilled, CheckmarkOutline} from '#/operate/shared/StateIcon/styled';
import {VisuallyHiddenH1} from '#/operate/shared/VisuallyHiddenH1/VisuallyHiddenH1';
import {InstancesTable} from './InstancesTable';
import {OptionalFiltersFormGroup, type OptionalFilter, type OptionalFilterValues} from './OptionalFiltersFormGroup';
import {DecisionPanel, type DecisionDefinitionSelection} from './DecisionPanel';
import {useDecisionDefinitionVersions} from './useDecisionDefinitionVersions';
import type {DecisionsSearch} from './decisionsFilter';

type FiltersFormValues = OptionalFilterValues & {
	tenantId?: string;
	decisionDefinitionId?: string;
	decisionDefinitionVersion?: number;
};

type Props = DecisionsSearch;

type DecisionItem = {id: string; label: string};

const Decisions: React.FC<Props> = ({
	decisionDefinitionId,
	decisionDefinitionVersion,
	tenantId,
	evaluated,
	failed,
	decisionEvaluationInstanceKey,
	processInstanceKey,
	businessId,
	evaluationDateFrom,
	evaluationDateTo,
	sort,
}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const specificTenantId = isSpecificTenant(tenantId) ? tenantId : undefined;
	const {data} = useSuspenseQuery(decisionDefinitionsOptions(specificTenantId));
	const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);

	const optionalFilterValues = useMemo<OptionalFilterValues>(
		() => ({decisionEvaluationInstanceKey, processInstanceKey, businessId, evaluationDateFrom, evaluationDateTo}),
		[decisionEvaluationInstanceKey, processInstanceKey, businessId, evaluationDateFrom, evaluationDateTo],
	);

	const decisionDefinitionSelectionQuery = useQuery({
		...decisionDefinitionSelectionOptions({
			decisionDefinitionId,
			decisionDefinitionVersion,
			tenantId: specificTenantId,
		}),
		enabled: decisionDefinitionId !== undefined,
	});
	const decisionDefinitionVersionsQuery = useDecisionDefinitionVersions(decisionDefinitionId, specificTenantId);

	const decisionItems = useMemo<DecisionItem[]>(() => {
		const seen = new Set<string>();
		const definitions = [
			...data.items,
			...(decisionDefinitionSelectionQuery.data?.items ?? []),
			...(decisionDefinitionVersionsQuery.data ?? []),
		];
		const items = definitions.reduce<DecisionItem[]>((acc, def) => {
			if (!seen.has(def.decisionDefinitionId)) {
				seen.add(def.decisionDefinitionId);
				acc.push({id: def.decisionDefinitionId, label: def.name ?? def.decisionDefinitionId});
			}
			return acc;
		}, []);
		if (decisionDefinitionId !== undefined && !seen.has(decisionDefinitionId)) {
			items.push({id: decisionDefinitionId, label: decisionDefinitionId});
		}
		return items;
	}, [data, decisionDefinitionId, decisionDefinitionSelectionQuery.data, decisionDefinitionVersionsQuery.data]);

	const versionNumbers = useMemo<(number | undefined)[]>(() => {
		if (!decisionDefinitionId) {
			return [];
		}
		const definitions = [
			...data.items,
			...(decisionDefinitionSelectionQuery.data?.items ?? []),
			...(decisionDefinitionVersionsQuery.data ?? []),
		];
		const versionSet = new Set(
			definitions.filter((def) => def.decisionDefinitionId === decisionDefinitionId).map((def) => def.version),
		);
		if (decisionDefinitionVersion !== undefined) {
			versionSet.add(decisionDefinitionVersion);
		}
		const versions = [...versionSet].sort((a, b) => b - a);
		return [undefined, ...versions];
	}, [
		data,
		decisionDefinitionId,
		decisionDefinitionSelectionQuery.data,
		decisionDefinitionVersion,
		decisionDefinitionVersionsQuery.data,
	]);

	const selectedDecision = decisionItems.find((item) => item.id === decisionDefinitionId) ?? null;

	const decisionDefinitionSelection = useMemo<DecisionDefinitionSelection>(() => {
		if (!decisionDefinitionId || decisionDefinitionSelectionQuery.status !== 'success') {
			return {kind: 'no-match'};
		}

		const matches = decisionDefinitionSelectionQuery.data.items.filter(
			(definition) =>
				definition.decisionDefinitionId === decisionDefinitionId &&
				(decisionDefinitionVersion === undefined || definition.version === decisionDefinitionVersion) &&
				(specificTenantId === undefined || definition.tenantId === specificTenantId),
		);

		if (decisionDefinitionVersion === undefined) {
			const first = matches[0];
			return first === undefined
				? {kind: 'no-match'}
				: {kind: 'all-versions', definition: {name: first.name, decisionDefinitionId: first.decisionDefinitionId}};
		}

		const definition = matches[0];
		if (definition === undefined) {
			return {kind: 'no-match'};
		}
		if (new Set(matches.map(({tenantId: definitionTenantId}) => definitionTenantId)).size > 1) {
			return {
				kind: 'multiple-tenants',
				definition: {name: definition.name, decisionDefinitionId: definition.decisionDefinitionId},
			};
		}
		return {kind: 'single-version', definition};
	}, [
		decisionDefinitionId,
		decisionDefinitionSelectionQuery.data,
		decisionDefinitionSelectionQuery.status,
		decisionDefinitionVersion,
		specificTenantId,
	]);

	useEffect(() => {
		if (
			decisionDefinitionId === undefined ||
			decisionDefinitionSelectionQuery.status !== 'success' ||
			decisionDefinitionSelectionQuery.fetchStatus !== 'idle' ||
			decisionDefinitionSelection.kind !== 'no-match'
		) {
			return;
		}

		void navigate({
			to: '.',
			search: (prev) => ({...prev, decisionDefinitionId: undefined, decisionDefinitionVersion: undefined}),
			replace: true,
		});
		notificationsStore.displayNotification({
			kind: 'error',
			title: t('operate.decisions.diagramPanel.decisionNotFoundTitle'),
			isDismissable: true,
		});
	}, [
		decisionDefinitionId,
		decisionDefinitionSelection,
		decisionDefinitionSelectionQuery.fetchStatus,
		decisionDefinitionSelectionQuery.status,
		navigate,
		t,
	]);

	const hasOptionalFilters =
		tenantId !== undefined || Object.values(optionalFilterValues).some((value) => value !== undefined);

	const isResetDisabled =
		evaluated &&
		failed &&
		!decisionDefinitionId &&
		decisionDefinitionVersion === undefined &&
		!hasOptionalFilters &&
		visibleFilters.length === 0;

	const handleFiltersSubmit = (values: FiltersFormValues) => {
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
	};

	return (
		<>
			<VisuallyHiddenH1>{t('operate.decisions.title')}</VisuallyHiddenH1>
			<InstancesList
				type="decision"
				leftPanel={
					<Form<FiltersFormValues> onSubmit={handleFiltersSubmit} initialValues={{tenantId, ...optionalFilterValues}}>
						{({handleSubmit, form}) => (
							<StyledForm onSubmit={handleSubmit}>
								<AutoSubmit fieldsToSkipTimeout={['tenantId']} />
								<FiltersPanel
									localStorageKey="isDecisionsFiltersCollapsed"
									isResetButtonDisabled={isResetDisabled}
									onResetClick={() => {
										form.reset();
										setVisibleFilters([]);
										void navigate({to: '.', search: {}});
									}}
								>
									<Stack gap={8}>
										<Stack gap={5}>
											{getClientConfig().deployment.isMultiTenancyEnabled && (
												<div>
													<Title>{t('operate.decisions.filters.tenant')}</Title>
													<TenantField
														onChange={() => {
															void navigate({
																to: '.',
																search: (prev) => ({
																	...prev,
																	decisionDefinitionId: undefined,
																	decisionDefinitionVersion: undefined,
																}),
															});
														}}
													/>
												</div>
											)}
											<div>
												<Title>{t('operate.decisions.filters.decisionSection')}</Title>
												<Stack gap={5}>
													<ComboBox
														id="decision-name-filter"
														titleText={t('operate.decisions.filters.name')}
														placeholder={t('operate.decisions.filters.searchByName')}
														items={decisionItems}
														itemToString={(item) => item?.label ?? ''}
														selectedItem={selectedDecision}
														size="sm"
														onChange={({selectedItem}) => {
															void navigate({
																to: '.',
																search: (prev) => ({
																	...prev,
																	decisionDefinitionId: selectedItem?.id,
																	decisionDefinitionVersion: undefined,
																}),
															});
														}}
													/>
													<Dropdown
														id="decision-version-filter"
														titleText={t('operate.decisions.filters.version')}
														label={t('operate.decisions.filters.selectVersion')}
														items={versionNumbers}
														itemToString={(item) =>
															item === undefined || item === null
																? t('operate.decisions.filters.allVersions')
																: String(item)
														}
														selectedItem={decisionDefinitionVersion}
														disabled={!decisionDefinitionId}
														size="sm"
														onChange={({selectedItem}) => {
															void navigate({
																to: '.',
																search: (prev) => ({...prev, decisionDefinitionVersion: selectedItem ?? undefined}),
															});
														}}
													/>
												</Stack>
											</div>
											<div>
												<Title>{t('operate.decisions.filters.instancesStates')}</Title>
												<Stack gap={1}>
													<Checkbox
														id="filter-evaluated"
														labelText={
															<Stack orientation="horizontal" gap={3}>
																<CheckmarkOutline size={20} />
																<div>{t('operate.decisions.filters.evaluated')}</div>
															</Stack>
														}
														checked={evaluated}
														onChange={(_, {checked}) => {
															void navigate({to: '.', search: (prev) => ({...prev, evaluated: checked})});
														}}
													/>
													<Checkbox
														id="filter-failed"
														labelText={
															<Stack orientation="horizontal" gap={3}>
																<WarningFilled size={20} />
																<div>{t('operate.decisions.filters.failed')}</div>
															</Stack>
														}
														checked={failed}
														onChange={(_, {checked}) => {
															void navigate({to: '.', search: (prev) => ({...prev, failed: checked})});
														}}
													/>
												</Stack>
											</div>
											<OptionalFiltersFormGroup
												filters={optionalFilterValues}
												visibleFilters={visibleFilters}
												onVisibleFilterChange={setVisibleFilters}
											/>
										</Stack>
									</Stack>
								</FiltersPanel>
							</StyledForm>
						)}
					</Form>
				}
				topPanel={
					<DecisionPanel
						decisionDefinitionSelection={decisionDefinitionSelection}
						isDefinitionSelectionLoading={
							decisionDefinitionId !== undefined &&
							(decisionDefinitionSelectionQuery.isPending || decisionDefinitionSelectionQuery.fetchStatus !== 'idle')
						}
						isDefinitionSelectionError={decisionDefinitionSelectionQuery.isError}
					/>
				}
				bottomPanel={
					<InstancesTable
						search={{
							decisionDefinitionId,
							decisionDefinitionVersion,
							tenantId,
							evaluated,
							failed,
							decisionEvaluationInstanceKey,
							processInstanceKey,
							businessId,
							evaluationDateFrom,
							evaluationDateTo,
							sort,
						}}
					/>
				}
			/>
		</>
	);
};

export {Decisions};
