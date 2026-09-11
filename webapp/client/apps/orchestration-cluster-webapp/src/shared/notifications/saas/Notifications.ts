/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {connectSse, type NotificationStream} from './sse';
import {keepAliveSchema, notificationFeedSchema, notificationSchema, notificationSseDataSchema} from './schemas';
import type {
	Notification,
	NotificationsConfig,
	NotificationsEvent,
	NotificationsEventMap,
	NotificationsFailure,
	NotificationsListener,
} from './types';

class NotificationHttpError extends Error {
	readonly status: number;

	constructor(status: number) {
		super(`Notification request failed with status ${status}`);
		this.name = 'NotificationHttpError';
		this.status = status;
	}
}

function toError(error: unknown): Error {
	return error instanceof Error ? error : new Error('Unknown notification error');
}

class Notifications {
	private readonly organizationId: string;
	private readonly url: string;
	private readonly getAccessToken: () => Promise<string>;
	private readonly fetch: typeof globalThis.fetch;
	private readonly listeners = new Map<NotificationsEvent, Set<(payload: never) => void>>();
	private items = new Map<string, Notification>();
	private pendingReads = new Set<string>();
	private pendingDismissals = new Set<string>();
	private bufferedNotifications: Notification[] = [];
	private stream?: NotificationStream;
	private loadController?: AbortController;
	private loadPromise?: Promise<void>;
	private isTrailingLoadRequested = false;
	private isConnected = false;
	private hasEmittedConnect = false;
	private generation = 0;

	constructor(config: NotificationsConfig) {
		this.organizationId = config.organizationId;
		this.url = config.url.replace(/\/+$/, '');
		this.getAccessToken = config.getAccessToken;
		this.fetch = config.fetch ?? globalThis.fetch;
	}

	on<Event extends NotificationsEvent>(event: Event, listener: NotificationsListener<Event>): () => void {
		const listeners = this.listeners.get(event) ?? new Set();
		listeners.add(listener as (payload: never) => void);
		this.listeners.set(event, listeners);
		return () => listeners.delete(listener as (payload: never) => void);
	}

	connect(): void {
		if (this.isConnected) {
			return;
		}
		this.isConnected = true;
		this.hasEmittedConnect = false;
		this.generation += 1;
		const generation = this.generation;
		this.stream = connectSse({
			url: `${this.url}/notifications/events`,
			getAccessToken: this.requireAccessToken,
			fetch: this.fetch,
			onOpen: () => {
				if (!this.isCurrent(generation)) {
					return;
				}
				this.synchronize();
			},
			onMessage: (data) => this.handleStreamData(data, generation),
			onError: (error) => this.emitError('stream', error),
		});
		this.synchronize();
	}

	disconnect(): void {
		if (!this.isConnected) {
			return;
		}
		this.isConnected = false;
		this.generation += 1;
		this.loadController?.abort();
		this.loadController = undefined;
		this.loadPromise = undefined;
		this.isTrailingLoadRequested = false;
		this.bufferedNotifications = [];
		this.pendingReads.clear();
		this.pendingDismissals.clear();
		this.stream?.close();
		this.stream = undefined;
	}

	refresh(): void {
		this.synchronize();
	}

	send(command: 'read', notificationId: string): void;
	send(command: 'readAll'): void;
	send(command: 'dismiss', notificationId: string): void;
	send(command: 'dismissAll'): void;
	send(command: 'read' | 'readAll' | 'dismiss' | 'dismissAll', notificationId?: string): void {
		if (!this.isConnected) {
			return;
		}

		if (command === 'read' && notificationId !== undefined) {
			const notification = this.snapshot().find(({uuid}) => uuid === notificationId);
			if (notification?.state !== 'new') {
				return;
			}
			this.pendingReads.add(notificationId);
			this.emitChange();
			this.persist(
				'read',
				() => this.request(`notifications/${encodeURIComponent(notificationId)}/read`, {method: 'PATCH'}),
				[notificationId],
			);
			return;
		}

		if (command === 'readAll') {
			const ids = this.snapshot()
				.filter(({state}) => state === 'new')
				.map(({uuid}) => uuid);
			if (ids.length === 0) {
				return;
			}
			ids.forEach((id) => this.pendingReads.add(id));
			this.emitChange();
			this.persist(
				'readAll',
				() => this.request('notifications/batch/state/read', {method: 'PATCH', body: JSON.stringify({uuids: ids})}),
				ids,
			);
			return;
		}

		if (command === 'dismiss' && notificationId !== undefined) {
			if (!this.snapshot().some(({uuid}) => uuid === notificationId)) {
				return;
			}
			this.pendingDismissals.add(notificationId);
			this.emitChange();
			this.persist(
				'dismiss',
				() => this.request(`notifications/${encodeURIComponent(notificationId)}/dismiss`, {method: 'PATCH'}),
				[notificationId],
			);
			return;
		}

		if (command === 'dismissAll') {
			const ids = this.snapshot().map(({uuid}) => uuid);
			if (ids.length === 0) {
				return;
			}
			ids.forEach((id) => this.pendingDismissals.add(id));
			this.emitChange();
			this.persist(
				'dismissAll',
				() =>
					this.request(`notifications/dismissAll?orgId=${encodeURIComponent(this.organizationId)}`, {method: 'PATCH'}),
				ids,
			);
		}
	}

	private readonly requireAccessToken = async (): Promise<string> => {
		const token = await this.getAccessToken();
		if (token.length === 0) {
			throw new Error('SaaS access token is unavailable');
		}
		return token;
	};

	private isCurrent(generation: number): boolean {
		return this.isConnected && generation === this.generation;
	}

	private emit<Event extends NotificationsEvent>(event: Event, payload: NotificationsEventMap[Event]): void {
		for (const listener of [...(this.listeners.get(event) ?? [])]) {
			try {
				listener(payload as never);
			} catch {
				// One listener must not prevent other listeners from receiving the event.
			}
		}
	}

	private emitError(operation: NotificationsFailure['operation'], error: unknown): void {
		this.emit('error', {operation, error: toError(error)});
	}

	private emitChange(): void {
		this.emit('change', this.snapshot());
	}

	private snapshot(): readonly Notification[] {
		return [...this.items.values()]
			.filter(({uuid}) => !this.pendingDismissals.has(uuid))
			.map((notification) =>
				this.pendingReads.has(notification.uuid) && notification.state === 'new'
					? {...notification, state: 'read' as const}
					: notification,
			)
			.sort((left, right) => right.timestamp - left.timestamp || left.uuid.localeCompare(right.uuid));
	}

	private apply(notification: Notification, target = this.items): void {
		if (notification.type === 'org' && notification.orgId !== this.organizationId) {
			return;
		}
		if (notification.state === 'new' || notification.state === 'read') {
			target.set(notification.uuid, notification);
		} else {
			target.delete(notification.uuid);
			if (target === this.items) {
				this.pendingReads.delete(notification.uuid);
				this.pendingDismissals.delete(notification.uuid);
			}
		}
		if (target === this.items && notification.state === 'read') {
			this.pendingReads.delete(notification.uuid);
		}
	}

	private handleStreamData(data: string, generation: number): void {
		if (!this.isCurrent(generation) || data.length === 0) {
			return;
		}
		try {
			const result = notificationSseDataSchema.safeParse(JSON.parse(data));
			if (!result.success) {
				this.emitError(
					'validation',
					new Error(result.error.issues.map(({path, message}) => `${path.join('.')}: ${message}`).join('; ')),
				);
				return;
			}
			if (keepAliveSchema.safeParse(result.data).success) {
				return;
			}
			const notification = result.data as Notification;
			if (this.loadPromise !== undefined) {
				this.bufferedNotifications.push(notification);
				if (!this.hasEmittedConnect) {
					return;
				}
			}
			this.apply(notification);
			this.emitChange();
		} catch (error) {
			this.emitError('validation', error);
		}
	}

	private async request(path: string, init: RequestInit): Promise<Response> {
		const token = await this.requireAccessToken();
		const response = await this.fetch(`${this.url}/${path}`, {
			...init,
			headers: {
				...(init.body === undefined ? {} : {'Content-Type': 'application/json'}),
				...init.headers,
				Authorization: `Bearer ${token}`,
			},
		});
		if (!response.ok) {
			throw new NotificationHttpError(response.status);
		}
		return response;
	}

	private synchronize(): void {
		if (!this.isConnected) {
			return;
		}
		if (this.loadPromise !== undefined) {
			this.isTrailingLoadRequested = true;
			return;
		}

		const generation = this.generation;
		this.bufferedNotifications = [];
		this.loadController = new AbortController();
		this.loadPromise = this.load(generation, this.loadController.signal).finally(() => {
			if (!this.isCurrent(generation)) {
				return;
			}
			this.loadPromise = undefined;
			this.loadController = undefined;
			if (this.isTrailingLoadRequested) {
				this.isTrailingLoadRequested = false;
				this.synchronize();
			}
		});
	}

	private async load(generation: number, signal: AbortSignal): Promise<void> {
		try {
			const response = await this.request(`notifications/orgs/${encodeURIComponent(this.organizationId)}`, {
				method: 'GET',
				signal,
			});
			if (!this.isCurrent(generation)) {
				return;
			}
			const text = await response.text();
			const body = notificationFeedSchema.safeParse(text.length === 0 ? [] : JSON.parse(text));
			if (!body.success) {
				throw new Error('Notification response is not an array');
			}

			const next = new Map<string, Notification>();
			let validItemCount = 0;
			body.data.forEach((item, index) => {
				const result = notificationSchema.safeParse(item);
				if (result.success) {
					validItemCount += 1;
					this.apply(result.data, next);
				} else {
					this.emitError('validation', new Error(`Invalid notification at feed index ${index}`));
				}
			});
			if (body.data.length > 0 && validItemCount === 0) {
				throw new Error('Notification response contains no valid items');
			}
			if (!this.isCurrent(generation)) {
				return;
			}
			for (const notification of this.bufferedNotifications) {
				this.apply(notification, next);
			}
			this.bufferedNotifications = [];
			this.items = next;
			this.reconcilePending();
			const snapshot = this.snapshot();
			if (!this.hasEmittedConnect) {
				this.hasEmittedConnect = true;
				this.emit('connect', snapshot);
			} else {
				this.emit('change', snapshot);
			}
		} catch (error) {
			if (!signal.aborted && this.isCurrent(generation)) {
				this.bufferedNotifications = [];
				this.emitError('connect', error);
			}
		}
	}

	private reconcilePending(): void {
		for (const id of [...this.pendingReads]) {
			const notification = this.items.get(id);
			if (notification === undefined || notification.state === 'read') {
				this.pendingReads.delete(id);
			}
		}
		for (const id of [...this.pendingDismissals]) {
			if (!this.items.has(id)) {
				this.pendingDismissals.delete(id);
			}
		}
	}

	private persist(
		operation: Extract<NotificationsFailure['operation'], 'read' | 'readAll' | 'dismiss' | 'dismissAll'>,
		request: () => Promise<Response>,
		ids: readonly string[],
	): void {
		const generation = this.generation;
		void request()
			.catch((error: unknown) => {
				if (!this.isCurrent(generation)) {
					return;
				}
				const pending = operation === 'read' || operation === 'readAll' ? this.pendingReads : this.pendingDismissals;
				ids.forEach((id) => pending.delete(id));
				this.emitError(operation, error);
				this.emitChange();
			})
			.finally(() => {
				if (this.isCurrent(generation)) {
					this.synchronize();
				}
			});
	}
}

export {Notifications};
