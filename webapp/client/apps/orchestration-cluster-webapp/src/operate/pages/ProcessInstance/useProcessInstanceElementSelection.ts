/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createContext, useCallback, useContext, useEffect, useRef} from 'react';
import {skipToken, useQuery} from '@tanstack/react-query';
import {useNavigate, useRouter} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import type {ElementInstance, QueryElementInstancesResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {ForbiddenError} from '#/shared/errors';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {historySort, instanceRequest} from './processInstance.queries';
import type {ProcessInstanceSelection} from './processInstanceSearch';
function useResolvedSelection(
	processInstanceKey: string,
	selection: ProcessInstanceSelection,
	onHistoryForbidden: (error: ForbiddenError) => void,
) {
	const navigate = useNavigate();
	const router = useRouter();
	const {t} = useTranslation();
	const anchor = useRef({version: 0, controller: new AbortController()});
	useEffect(() => {
		const current = anchor.current;
		const cancel = () => {
			current.version++;
			current.controller.abort();
		};
		const unsubscribe = router.subscribe('onBeforeNavigate', cancel);
		return () => {
			cancel();
			unsubscribe();
		};
	}, [router]);
	const {
		elementId,
		elementInstanceKey,
		isMultiInstanceBody = false,
		isPlaceholder = false,
		anchorElementId,
	} = selection;
	const hasSelection = Boolean(elementId || elementInstanceKey);
	const enabled = hasSelection && !(elementInstanceKey && isPlaceholder);
	const query = useQuery({
		queryKey: elementInstanceKey
			? ['elementInstance', elementInstanceKey]
			: ['selectedElementInstances', processInstanceKey, elementId, isMultiInstanceBody],
		queryFn: !enabled
			? skipToken
			: ({signal}) =>
					instanceRequest<ElementInstance | QueryElementInstancesResponseBody>(
						elementInstanceKey
							? endpoints.getElementInstance(elementInstanceKey)
							: endpoints.queryElementInstances({
									filter: {
										processInstanceKey,
										elementId,
										...(isMultiInstanceBody ? {type: 'MULTI_INSTANCE_BODY'} : {}),
									},
									page: {limit: 1},
								}),
						signal,
					),
		staleTime: elementInstanceKey ? 0 : 10000,
		select: (data) =>
			'items' in data
				? {
						instance: data.page.totalItems === 1 && !data.page.hasMoreTotalItems ? (data.items[0] ?? null) : null,
						count: data.page.totalItems,
					}
				: {instance: data, count: 1},
	});
	const result = enabled ? query.data : undefined;
	const replaceSelection = useCallback(
		(next: ProcessInstanceSelection) => {
			anchor.current.version++;
			anchor.current.controller.abort();
			void navigate({
				to: '.',
				replace: true,
				search: (current) => ({
					...current,
					elementId: undefined,
					elementInstanceKey: undefined,
					isMultiInstanceBody: undefined,
					isPlaceholder: undefined,
					anchorElementId: undefined,
					...next,
				}),
			});
		},
		[navigate],
	);
	const clearSelection = useCallback(() => replaceSelection({}), [replaceSelection]);
	const selectElement = useCallback(
		(id: string, multiInstanceBody = false) =>
			replaceSelection({elementId: id, isMultiInstanceBody: multiInstanceBody || undefined}),
		[replaceSelection],
	);
	const selectElementInstance = useCallback(
		async (instance: ElementInstance, anchorElementId?: string) => {
			const current = anchor.current;
			const version = ++current.version;
			current.controller.abort();
			current.controller = new AbortController();
			if (instance.type === 'AD_HOC_SUB_PROCESS_INNER_INSTANCE' && !anchorElementId) {
				try {
					const result = await instanceRequest<QueryElementInstancesResponseBody>(
						endpoints.queryElementInstances({
							filter: {elementInstanceScopeKey: instance.elementInstanceKey},
							sort: historySort,
							page: {from: 0, limit: 1},
						}),
						current.controller.signal,
					);
					if (version !== current.version) {
						return;
					}
					anchorElementId = result.items[0]?.elementId;
					if (!anchorElementId) {
						throw new Error('Missing anchor');
					}
				} catch (error) {
					if (version === current.version) {
						if (error instanceof ForbiddenError) {
							onHistoryForbidden(error);
						} else {
							notificationsStore.displayNotification({
								kind: 'warning',
								title: t('operate.processInstance.history.anchorError'),
								isDismissable: true,
							});
						}
					}
					return;
				}
			}
			replaceSelection({
				elementId: instance.elementId,
				elementInstanceKey: instance.elementInstanceKey,
				isMultiInstanceBody: instance.type === 'MULTI_INSTANCE_BODY' || undefined,
				anchorElementId,
			});
		},
		[replaceSelection, t, onHistoryForbidden],
	);
	const isSelected = useCallback(
		(id: string, key?: string, multiInstanceBody = false) =>
			multiInstanceBody === isMultiInstanceBody && (elementInstanceKey ? key === elementInstanceKey : id === elementId),
		[elementId, elementInstanceKey, isMultiInstanceBody],
	);
	return {
		resolvedElementInstance: result?.instance ?? null,
		selectedElementId: elementId ?? null,
		selectedElementInstanceKey: elementInstanceKey ?? null,
		selectedElementInstanceCount: result?.count ?? null,
		hasSelection,
		isSelectedInstanceMultiInstanceBody: isMultiInstanceBody,
		isSelectedInstancePlaceholder: isPlaceholder,
		anchorElementId,
		isFetchingElement: enabled && query.isFetching,
		isFetchingElementError: enabled && query.isError,
		selectElement,
		selectElementInstance,
		clearSelection,
		isSelected,
	};
}
const ElementSelectionContext = createContext<ReturnType<typeof useResolvedSelection> | null>(null);
function useProcessInstanceElementSelection() {
	const value = useContext(ElementSelectionContext);
	if (!value) {
		throw new Error('ElementSelectionContext provider is required');
	}
	return value;
}
export {ElementSelectionContext, useResolvedSelection, useProcessInstanceElementSelection};
