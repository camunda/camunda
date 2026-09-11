/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type SseMessage = {data: string; event?: string; id?: string};
type NotificationStream = {close: () => void};

type Options = {
	url: string;
	getAccessToken: () => Promise<string>;
	onOpen: (isReconnect: boolean) => void;
	onMessage: (data: string) => void;
	onError?: (error: unknown) => void;
	fetch?: typeof globalThis.fetch;
	heartbeatTimeoutMs?: number;
	setTimeout?: typeof globalThis.setTimeout;
	clearTimeout?: typeof globalThis.clearTimeout;
	random?: () => number;
};

const DEFAULT_HEARTBEAT_TIMEOUT_MS = 120_000;
const BASE_RECONNECT_DELAY_MS = 1_000;
const MAX_RECONNECT_DELAY_MS = 30_000;

function createSseParser(onMessage: (message: SseMessage) => void, initialId?: string) {
	let buffer = '';
	let dataLines: string[] = [];
	let eventName: string | undefined;
	let lastEventId = initialId;

	function dispatch() {
		if (dataLines.length > 0 || eventName !== undefined) {
			onMessage({data: dataLines.join('\n'), event: eventName, id: lastEventId});
		}
		dataLines = [];
		eventName = undefined;
	}

	function processLine(line: string) {
		if (line === '') {
			dispatch();
			return;
		}
		if (line.startsWith(':')) {
			return;
		}

		const separator = line.indexOf(':');
		const field = separator === -1 ? line : line.slice(0, separator);
		let value = separator === -1 ? '' : line.slice(separator + 1);
		if (value.startsWith(' ')) {
			value = value.slice(1);
		}

		if (field === 'data') {
			dataLines.push(value);
		} else if (field === 'event') {
			eventName = value;
		} else if (field === 'id' && !value.includes('\u0000')) {
			lastEventId = value;
		}
	}

	function push(chunk: string) {
		buffer += chunk;
		let lineStart = 0;
		for (let index = 0; index < buffer.length; index += 1) {
			const character = buffer[index];
			if (character !== '\n' && character !== '\r') {
				continue;
			}
			if (character === '\r' && index === buffer.length - 1) {
				break;
			}

			processLine(buffer.slice(lineStart, index));
			if (character === '\r' && buffer[index + 1] === '\n') {
				index += 1;
			}
			lineStart = index + 1;
		}
		buffer = buffer.slice(lineStart);
	}

	return {
		push,
		finish() {
			if (buffer.endsWith('\r')) {
				push('\n');
			}
			if (buffer.length > 0) {
				processLine(buffer);
				buffer = '';
			}
			dispatch();
		},
		getLastEventId: () => lastEventId,
	};
}

function connectSse(options: Options): NotificationStream {
	const fetchImpl = options.fetch ?? globalThis.fetch;
	const setTimeoutImpl = options.setTimeout ?? globalThis.setTimeout;
	const clearTimeoutImpl = options.clearTimeout ?? globalThis.clearTimeout;
	const random = options.random ?? Math.random;
	const heartbeatTimeoutMs = options.heartbeatTimeoutMs ?? DEFAULT_HEARTBEAT_TIMEOUT_MS;
	let isClosed = false;
	let hasOpened = false;
	let attempt = 0;
	let isAuthRetryUsed = false;
	let lastEventId: string | undefined;
	let abortController: AbortController | undefined;
	let reader: ReadableStreamDefaultReader<Uint8Array> | undefined;
	let heartbeatTimer: ReturnType<typeof setTimeout> | undefined;
	let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
	let resolveReconnect: (() => void) | undefined;

	function reportError(error: unknown) {
		try {
			options.onError?.(error);
		} catch {
			// Observer failures must not affect the connection lifecycle.
		}
	}

	function notifyOpen() {
		try {
			options.onOpen(hasOpened);
		} catch (error) {
			reportError(error);
		}
		hasOpened = true;
	}

	function clearHeartbeat() {
		if (heartbeatTimer !== undefined) {
			clearTimeoutImpl(heartbeatTimer);
			heartbeatTimer = undefined;
		}
	}

	function armHeartbeat() {
		clearHeartbeat();
		heartbeatTimer = setTimeoutImpl(() => abortController?.abort(), heartbeatTimeoutMs);
	}

	function waitToReconnect(): Promise<void> {
		if (isClosed) {
			return Promise.resolve();
		}
		const maximumDelay = Math.min(MAX_RECONNECT_DELAY_MS, BASE_RECONNECT_DELAY_MS * 2 ** attempt);
		attempt += 1;
		return new Promise((resolve) => {
			resolveReconnect = resolve;
			reconnectTimer = setTimeoutImpl(
				() => {
					reconnectTimer = undefined;
					resolveReconnect = undefined;
					resolve();
				},
				Math.max(1, Math.floor(random() * maximumDelay)),
			);
		});
	}

	function validateResponse(response: Response) {
		if (response.body === null) {
			throw new Error('Notification stream response has no body');
		}
		const contentType = response.headers.get('Content-Type');
		if (contentType === null || !contentType.toLowerCase().includes('text/event-stream')) {
			throw new Error('Notification stream response has an invalid content type');
		}
	}

	async function readStream(response: Response) {
		if (response.body === null) {
			throw new Error('Notification stream response has no body');
		}
		const parser = createSseParser((message) => {
			if (!isClosed) {
				try {
					options.onMessage(message.data);
				} catch (error) {
					reportError(error);
				}
			}
		}, lastEventId);
		reader = response.body.getReader();
		const decoder = new TextDecoder();
		armHeartbeat();

		try {
			while (!isClosed) {
				const {done, value} = await reader.read();
				if (done) {
					parser.push(decoder.decode());
					parser.finish();
					lastEventId = parser.getLastEventId();
					return;
				}
				armHeartbeat();
				parser.push(decoder.decode(value, {stream: true}));
				lastEventId = parser.getLastEventId();
			}
		} finally {
			clearHeartbeat();
			reader.releaseLock();
			reader = undefined;
		}
	}

	async function run() {
		while (!isClosed) {
			abortController = new AbortController();
			try {
				const token = await options.getAccessToken();
				if (isClosed) {
					return;
				}
				const headers: Record<string, string> = {
					Accept: 'text/event-stream',
					Authorization: `Bearer ${token}`,
				};
				if (lastEventId !== undefined) {
					headers['Last-Event-ID'] = lastEventId;
				}

				const response = await fetchImpl(options.url, {
					method: 'GET',
					headers,
					signal: abortController.signal,
				});
				if (response.status === 204) {
					break;
				}
				if (!response.ok) {
					if ((response.status === 401 || response.status === 403) && !isAuthRetryUsed) {
						isAuthRetryUsed = true;
						continue;
					}
					throw new Error(`Notification stream request failed with status ${response.status}`);
				}

				validateResponse(response);
				attempt = 0;
				isAuthRetryUsed = false;
				notifyOpen();
				await readStream(response);
			} catch (error) {
				if (isClosed) {
					return;
				}
				reportError(error);
			} finally {
				clearHeartbeat();
				abortController = undefined;
			}
			await waitToReconnect();
		}
		if (!isClosed) {
			isClosed = true;
		}
	}

	void run();

	return {
		close() {
			if (isClosed) {
				return;
			}
			isClosed = true;
			clearHeartbeat();
			if (reconnectTimer !== undefined) {
				clearTimeoutImpl(reconnectTimer);
				reconnectTimer = undefined;
			}
			resolveReconnect?.();
			resolveReconnect = undefined;
			void reader?.cancel().catch(() => undefined);
			abortController?.abort();
		},
	};
}

export {connectSse};
export type {NotificationStream};
