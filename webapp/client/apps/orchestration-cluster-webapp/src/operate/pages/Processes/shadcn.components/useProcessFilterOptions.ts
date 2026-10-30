/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {keepPreviousData, useQuery, useSuspenseQuery} from '@tanstack/react-query';
import {useNavigate} from '@tanstack/react-router';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {queries} from '#/shared/http/queries';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';
import {latestDefinitionsQuery, selectedDefinitionsQuery} from '#/operate/shared/queries/processDefinitions.queries';
import {useDiagramXml} from '../useDiagramXml';

type Params = {
	process?: string;
	version?: number;
	elementId?: string;
	tenantId?: string;
};

type ProcessItem = {
	id: string;
	processDefinitionId: string;
	tenantId?: string;
	name: string;
	label: string;
	version: number;
};

function useProcessFilterOptions({process, version, elementId, tenantId}: Params) {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const specificTenantId = isSpecificTenant(tenantId) ? tenantId : undefined;
	const isMultiTenancyEnabled = getClientConfig().deployment.isMultiTenancyEnabled;
	const {data} = useSuspenseQuery(latestDefinitionsQuery(specificTenantId));
	const {data: tenantsById} = useQuery({
		...queries.getCurrentUser(),
		select: ({tenants}) => Object.fromEntries(tenants.map(({tenantId, name}) => [tenantId, name])),
		enabled: isMultiTenancyEnabled,
	});
	const {
		data: selectedDefinitions,
		error: selectedDefinitionsError,
		isPending: isDefinitionsPending,
		isFetching: isDefinitionsFetching,
		isError: isDefinitionsError,
		isPlaceholderData: isDefinitionsPlaceholder,
		refetch: refetchSelectedDefinitions,
	} = useQuery({
		...selectedDefinitionsQuery(process ?? '', specificTenantId, {
			retry: false,
			requireComplete: true,
			selectionVersion: version ?? 'all',
		}),
		enabled: Boolean(process),
		placeholderData: keepPreviousData,
		retry: false,
	});
	const isDefinitionsLoading =
		Boolean(process) && (isDefinitionsPending || (isDefinitionsError && isDefinitionsFetching));
	const isDefinitionsReady =
		Boolean(process) && selectedDefinitions !== undefined && !isDefinitionsLoading && !isDefinitionsError;

	const processItems = useMemo<ProcessItem[]>(() => {
		const toItem = (definition: ProcessDefinition): ProcessItem => {
			const name = definition.name ?? definition.processDefinitionId;
			return {
				id: `${definition.processDefinitionId}##${definition.tenantId}`,
				processDefinitionId: definition.processDefinitionId,
				tenantId: definition.tenantId,
				name,
				label:
					isMultiTenancyEnabled && specificTenantId === undefined
						? `${name} - ${tenantsById?.[definition.tenantId] ?? definition.tenantId}`
						: name,
				version: definition.version,
			};
		};
		const items = data.map(toItem).sort((a, b) => a.label.localeCompare(b.label));
		const selected = isDefinitionsReady
			? selectedDefinitions?.find(
					(definition) =>
						definition.processDefinitionId === process &&
						(specificTenantId === undefined || definition.tenantId === specificTenantId),
				)
			: undefined;
		if (selected !== undefined && !items.some(({id}) => id === toItem(selected).id)) {
			items.push(toItem(selected));
		}
		return items;
	}, [data, isDefinitionsReady, isMultiTenancyEnabled, process, selectedDefinitions, specificTenantId, tenantsById]);

	const matchingDefinitions = useMemo(
		() =>
			isDefinitionsReady
				? (selectedDefinitions ?? []).filter(
						(definition) =>
							definition.processDefinitionId === process &&
							(specificTenantId === undefined || definition.tenantId === specificTenantId),
					)
				: [],
		[isDefinitionsReady, process, selectedDefinitions, specificTenantId],
	);

	const versionNumbers = useMemo<(number | 'all')[]>(() => {
		if (!isDefinitionsReady || matchingDefinitions.length === 0) {
			return [];
		}
		const versions = [...new Set(matchingDefinitions.map((def) => def.version))].sort((a, b) => b - a);
		return versions.length > 1 || version === undefined ? ['all', ...versions] : versions;
	}, [isDefinitionsReady, matchingDefinitions, version]);

	const selectedProcess = useMemo<ProcessItem | null>(() => {
		const candidates = processItems.filter(
			(item) =>
				item.processDefinitionId === process && (specificTenantId === undefined || item.tenantId === specificTenantId),
		);
		const [first] = candidates;
		if (first === undefined || candidates.length === 1) {
			return first ?? null;
		}
		const versionTenants = new Set(
			matchingDefinitions
				.filter((definition) => version === undefined || definition.version === version)
				.map((definition) => definition.tenantId),
		);
		const tenantItem =
			versionTenants.size === 1 ? candidates.find((item) => versionTenants.has(item.tenantId ?? '')) : undefined;
		return tenantItem ?? {...first, id: first.processDefinitionId, tenantId: undefined, label: first.name};
	}, [matchingDefinitions, process, processItems, specificTenantId, version]);

	// A process or version in the URL that matches no definition is dropped, like on the Carbon page.
	const hasMatch =
		matchingDefinitions.length > 0 &&
		(version === undefined || matchingDefinitions.some((definition) => definition.version === version));
	useEffect(() => {
		if (!isDefinitionsReady || isDefinitionsFetching || isDefinitionsPlaceholder || hasMatch) {
			return;
		}
		void navigate({to: '.', search: (prev) => ({...prev, process: undefined, version: undefined}), hash: true});
		notificationsStore.displayNotification({
			kind: 'error',
			title: t('operate.processes.diagramPanel.processNotFoundTitle'),
			isDismissable: true,
		});
	}, [isDefinitionsReady, isDefinitionsFetching, isDefinitionsPlaceholder, hasMatch, navigate, t]);

	// Elements are only selectable once a single, unambiguous definition version is chosen.
	const singleDefinition = useMemo(() => {
		if (!isDefinitionsReady || version === undefined) {
			return undefined;
		}
		const candidates = matchingDefinitions.filter((candidate) => candidate.version === version);
		const [first] = candidates;
		const isAmbiguous = !specificTenantId && candidates.some((candidate) => candidate.tenantId !== first?.tenantId);
		return isAmbiguous ? undefined : first;
	}, [isDefinitionsReady, matchingDefinitions, specificTenantId, version]);
	const selectedDefinitionKey = singleDefinition?.processDefinitionKey;
	const {
		data: diagramData,
		error: xmlError,
		isError: isXmlError,
		refetch: refetchDiagramXml,
	} = useDiagramXml(selectedDefinitionKey);
	const elementItems = useMemo(
		() =>
			(diagramData?.selectableElements ?? [])
				.map((id) => ({id, label: diagramData?.businessObjects[id]?.name ?? id}))
				.sort((a, b) => {
					const label = a.label.toUpperCase();
					const nextLabel = b.label.toUpperCase();
					return label < nextLabel ? -1 : label > nextLabel ? 1 : 0;
				}),
		[diagramData],
	);
	const selectedElement = useMemo(
		() => elementItems.find((item) => item.id === elementId) ?? (elementId ? {id: elementId, label: elementId} : null),
		[elementItems, elementId],
	);
	const isElementDisabled =
		!isDefinitionsReady || selectedDefinitionKey === undefined || isXmlError || elementItems.length === 0;

	return {
		processItems,
		selectedProcess,
		versionNumbers,
		isDefinitionsReady,
		isDefinitionsError,
		isDefinitionsFetching,
		selectedDefinitionsError,
		refetchSelectedDefinitions,
		selectedDefinitionKey,
		elementItems,
		selectedElement,
		isElementDisabled,
		isXmlError,
		xmlError,
		refetchDiagramXml,
		isMultiTenancyEnabled,
	};
}

export {useProcessFilterOptions};
