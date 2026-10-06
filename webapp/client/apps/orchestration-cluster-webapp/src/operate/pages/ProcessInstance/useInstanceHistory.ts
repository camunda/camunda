/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createContext, useContext, useEffect, useEffectEvent, useRef, useState} from 'react';
import {queryOptions, useQueries, useQueryClient} from '@tanstack/react-query';
import type {ProcessInstance, QueryElementInstancesResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {ForbiddenError} from '#/shared/errors';
import {historySort, instanceRequest} from './processInstance.queries';

type Scope = {key: string; parent?: string; from: number; version: number};
function historyWindowQuery(instance: string, scope: Scope) {
	return queryOptions({
		queryKey: ['instanceHistory', instance, scope.key, scope.version, scope.from] as const,
		queryFn: ({signal}) =>
			instanceRequest<QueryElementInstancesResponseBody>(
				endpoints.queryElementInstances({
					filter: {elementInstanceScopeKey: scope.key},
					page: {from: scope.from, limit: 100},
					sort: historySort,
				}),
				signal,
			),
		retry: false,
		staleTime: Infinity,
		refetchOnWindowFocus: false,
	});
}
function useHistoryController(instance: ProcessInstance) {
	const instanceKey = instance.processInstanceKey;
	const client = useQueryClient();
	const [scopes, setScopes] = useState<Scope[]>([{key: instanceKey, from: 0, version: 0}]);
	const [visible, setVisible] = useState(false);
	const [timestamps, setTimestamps] = useState(false);
	const [executionCount, setExecutionCount] = useState(false);
	const [pageError, setPageError] = useState<unknown>(null);
	const [scopeErrors, setScopeErrors] = useState<Record<string, unknown>>({});
	const version = useRef(0);
	const lifecycle = useRef({version: 0});
	const polling = useRef(false);
	const pending = useRef(new Map<string, number>());
	const queries = useQueries({
		queries: scopes.map((scope) => ({
			...historyWindowQuery(instanceKey, scope),
			enabled: visible && !(pageError instanceof ForbiddenError),
		})),
	});
	const forbiddenError =
		pageError instanceof ForbiddenError ? pageError : queries.find(({error}) => error instanceof ForbiddenError)?.error;
	const forbidden = Boolean(forbiddenError);
	const windows = new Map(
		scopes.map((scope, index) => [scope.key, {scope, query: queries[index]!, error: scopeErrors[scope.version]}]),
	);
	useEffect(() => {
		void client.invalidateQueries({queryKey: ['instanceHistory', instanceKey]});
	}, [client, instanceKey]);
	useEffect(() => {
		if (forbiddenError) {
			void client.cancelQueries({queryKey: ['instanceHistory', instanceKey]}).then(() => setPageError(forbiddenError));
		}
	}, [client, forbiddenError, instanceKey]);
	useEffect(() => {
		const currentLifecycle = lifecycle.current;
		if (!visible) {
			currentLifecycle.version++;
			void client.cancelQueries({queryKey: ['instanceHistory', instanceKey]});
			pending.current.clear();
		}
		return () => {
			currentLifecycle.version++;
			void client.cancelQueries({queryKey: ['instanceHistory', instanceKey]});
		};
	}, [client, instanceKey, visible]);
	function clearScopeErrors(versions: number[]) {
		setScopeErrors((current) =>
			Object.fromEntries(Object.entries(current).filter(([version]) => !versions.includes(Number(version)))),
		);
	}
	async function refreshScopes(requested: Scope[], fetchScope: (scope: Scope) => Promise<unknown>) {
		const generation = lifecycle.current.version;
		const results = await Promise.allSettled(requested.map(fetchScope));
		if (generation !== lifecycle.current.version) {
			return;
		}
		const recovered = requested.filter((_, index) => results[index]?.status === 'fulfilled');
		if (recovered.length > 0) {
			clearScopeErrors(recovered.map((scope) => scope.version));
			setPageError((error: unknown) => (error instanceof ForbiddenError ? error : null));
		}
	}
	const poll = useEffectEvent(async () => {
		if (document.hidden || polling.current || pending.current.size > 0 || queries.some(({isFetching}) => isFetching)) {
			return;
		}
		polling.current = true;
		const active = scopes.filter(
			({key}) =>
				key === instanceKey ||
				queries.some(({data}) =>
					data?.items.some((item) => item.elementInstanceKey === key && item.state === 'ACTIVE'),
				) ||
				windows.get(key)?.query.data?.items.some(({state}) => state === 'ACTIVE'),
		);
		await refreshScopes(active, (scope) =>
			client.fetchQuery({...historyWindowQuery(instanceKey, scope), staleTime: 0}),
		);
		polling.current = false;
	});
	useEffect(() => {
		if (!visible || forbidden || instance.state !== 'ACTIVE') {
			return;
		}
		const timer = window.setInterval(() => void poll(), 5000);
		return () => window.clearInterval(timer);
	}, [forbidden, instance.state, visible]);
	function toggle(key: string, parent?: string) {
		if (windows.has(key)) {
			const removed = new Set([key]);
			scopes.forEach((scope) => {
				if (scope.parent && removed.has(scope.parent)) {
					removed.add(scope.key);
				}
			});
			removed.forEach((scopeKey) => {
				pending.current.delete(scopeKey);
				void client.cancelQueries({queryKey: ['instanceHistory', instanceKey, scopeKey]});
			});
			clearScopeErrors(scopes.filter((scope) => removed.has(scope.key)).map((scope) => scope.version));
			setScopes((current) => current.filter((scope) => !removed.has(scope.key)));
		} else {
			setScopes((current) => [...current, {key, parent, from: 0, version: ++version.current}]);
		}
	}
	async function page(key: string, direction: 'next' | 'previous') {
		const window = windows.get(key);
		if (!window?.query.data || pending.current.has(key) || window.query.isFetching || forbidden) {
			return 0;
		}
		const {
			scope,
			query: {data},
		} = window;
		if (direction === 'next' ? scope.from + 100 >= data.page.totalItems : scope.from === 0) {
			return 0;
		}
		const next = {
			...scope,
			from: Math.max(0, scope.from + (direction === 'next' ? 50 : -50)),
			version: ++version.current,
		};
		const generation = lifecycle.current.version;
		pending.current.set(key, next.version);
		try {
			const result = await client.fetchQuery(historyWindowQuery(instanceKey, next));
			if (generation !== lifecycle.current.version || pending.current.get(key) !== next.version) {
				return -1;
			}
			setScopes((current) => current.map((entry) => (entry === scope ? next : entry)));
			setPageError(null);
			clearScopeErrors([scope.version]);
			return direction === 'next' ? Math.max(0, result.items.length - 50) : Math.min(50, result.items.length);
		} catch (error) {
			if (generation === lifecycle.current.version && pending.current.get(key) === next.version) {
				if (key === instanceKey || error instanceof ForbiddenError) {
					setPageError(error);
				} else {
					setScopeErrors((current) => ({...current, [scope.version]: error}));
				}
			}
			return -1;
		} finally {
			if (pending.current.get(key) === next.version) {
				pending.current.delete(key);
			}
		}
	}
	return {
		windows,
		forbidden,
		pageError,
		retry: () => {
			setPageError(null);
			void refreshScopes(scopes, (scope) => windows.get(scope.key)!.query.refetch({throwOnError: true}));
		},
		visible,
		setVisible,
		timestamps,
		setTimestamps,
		executionCount,
		setExecutionCount,
		toggle,
		page,
	};
}
const InstanceHistoryContext = createContext<ReturnType<typeof useHistoryController> | null>(null);
function useInstanceHistory() {
	const context = useContext(InstanceHistoryContext);
	if (!context) {
		throw new Error('InstanceHistoryContext provider is required');
	}
	return context;
}
export {useHistoryController, InstanceHistoryContext, useInstanceHistory};
