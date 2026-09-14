/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getSaasAccessToken} from './api';

const RECONNECT_DELAY_MS = 1_000;

function connectNotificationStream(url: string, onEvent: () => void): () => void {
	const controller = new AbortController();

	void (async () => {
		while (!controller.signal.aborted) {
			try {
				const token = await getSaasAccessToken();
				const response = await fetch(`${url.replace(/\/+$/u, '')}/notifications/events`, {
					headers: {Accept: 'text/event-stream', Authorization: `Bearer ${token}`},
					signal: controller.signal,
				});
				if (!response.ok || response.body === null) {
					throw new Error(`Notification stream request failed with status ${response.status}`);
				}

				onEvent();
				const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
				let buffer = '';
				while (!controller.signal.aborted) {
					const {done, value = ''} = await reader.read();
					buffer += value;
					const events = buffer.split(/\r?\n\r?\n/u);
					buffer = events.pop() ?? '';
					for (const event of events) {
						const data = event
							.split(/\r?\n/u)
							.filter((line) => line.startsWith('data:'))
							.map((line) => line.slice(5).trimStart())
							.join('\n');
						try {
							if (data !== '' && !JSON.parse(data).keepAlive) {
								onEvent();
							}
						} catch {
							// Ignore malformed events and keep the stream open.
						}
					}
					if (done) {
						break;
					}
				}
			} catch {
				// Reconnect unless the caller closed the stream.
			}
			if (!controller.signal.aborted) {
				await new Promise((resolve) => setTimeout(resolve, RECONNECT_DELAY_MS));
			}
		}
	})();

	return () => controller.abort();
}

export {connectNotificationStream};
