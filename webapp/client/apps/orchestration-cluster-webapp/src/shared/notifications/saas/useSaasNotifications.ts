/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {queries} from '#/shared/http/queries';
import {
	dismissAllNotifications,
	dismissNotification,
	markNotificationsAsRead,
	sendNotificationAnalytics,
	type AnalyticsEvent,
	type NotificationsConfig,
} from './api';
import {connectNotificationStream} from './sse';

function useSaasNotifications(config: NotificationsConfig, onMutationError: () => void) {
	const queryClient = useQueryClient();
	const queryOptions = queries.getSaasNotifications(config);
	const query = useQuery(queryOptions);
	const invalidate = () => queryClient.invalidateQueries({queryKey: queryOptions.queryKey});

	useEffect(() => {
		const queryKey = queries.getSaasNotifications({
			organizationId: config.organizationId,
			url: config.url,
		}).queryKey;
		return connectNotificationStream(config.url, () => void queryClient.invalidateQueries({queryKey}));
	}, [config.organizationId, config.url, queryClient]);

	const readMutation = useMutation({
		mutationFn: (notificationIds: readonly string[]) => markNotificationsAsRead(config, notificationIds),
		onSuccess: invalidate,
		onError: onMutationError,
	});
	const dismissMutation = useMutation({
		mutationFn: (notificationId: string) => dismissNotification(config, notificationId),
		onSuccess: invalidate,
		onError: onMutationError,
	});
	const dismissAllMutation = useMutation({
		mutationFn: () => dismissAllNotifications(config),
		onSuccess: invalidate,
		onError: onMutationError,
	});
	const sendAnalytics = useCallback(
		(event: AnalyticsEvent, identifier?: string) => {
			void sendNotificationAnalytics(config, event, identifier).catch(() => undefined);
		},
		[config],
	);

	return {
		notifications: query.data ?? [],
		isLoading: query.isPending,
		isError: query.isError,
		refetch: query.refetch,
		markAsRead: readMutation.mutate,
		dismiss: dismissMutation.mutate,
		dismissAll: dismissAllMutation.mutate,
		sendAnalytics,
	};
}

export {useSaasNotifications};
