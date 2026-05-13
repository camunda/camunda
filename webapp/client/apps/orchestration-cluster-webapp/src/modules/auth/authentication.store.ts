/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeObservable, observable, action} from 'mobx';
import {endpoints} from '#/modules/http/endpoints';
import {reactQueryClient} from '#/modules/http/reactQueryClient';
import {request} from '#/modules/http/request';
import {storeStateLocally} from '#/modules/local-storage/local-storage';
import {z} from 'zod';

type Status =
	| 'initial'
	| 'logged-in'
	| 'logged-out'
	| 'session-expired'
	| 'session-invalid'
	| 'invalid-third-party-session';

const DEFAULT_STATUS: Status = 'initial';

const logoutResponseSchema = z.object({
	url: z.url({message: 'No redirect URL provided'}),
});

async function parseRedirectUrl(response: Response): Promise<string> {
	const json = await response.json();
	const result = logoutResponseSchema.parse(json);
	return result.url;
}

class Authentication {
	status: Status = DEFAULT_STATUS;

	constructor() {
		makeObservable(this, {
			status: observable,
			setStatus: action,
			reset: action,
		});
	}

	handleLogin = async (username: string, password: string) => {
		const {response, error} = await request(endpoints.login({username, password}), {
			skipSessionCheck: true,
		});

		if (error === null) {
			this.activateSession();
			// TODO: prefetch current user after login once getClientConfig is migrated
			// https://github.com/camunda/camunda/issues/51322
		}

		return {response, error};
	};

	setStatus = (status: Status) => {
		this.status = status;
	};

	// TODO: handle third-party session expiration (IdP logout redirect / reload)
	// once getClientConfig is migrated: https://github.com/camunda/camunda/issues/51322
	handleLogout = async () => {
		const {response, error} = await request(endpoints.logout(), {
			skipSessionCheck: true,
		});

		if (error !== null) {
			return error;
		}

		reactQueryClient.clear();

		// TODO: handle getClientConfig().canLogout / isLoginDelegated (IdP RP-initiated logout)
		// https://github.com/camunda/camunda/issues/51322
		try {
			if (response.status === 200) {
				await parseRedirectUrl(response);
			}
		} catch {
			// ignore — standard 204 logout path
		}

		this.setStatus('logged-out');
		return;
	};

	activateSession = () => {
		this.setStatus('logged-in');
		storeStateLocally('wasReloaded', false);
	};

	disableSession = () => {
		// TODO: handle getClientConfig().canLogout / isLoginDelegated (third-party session expiration)
		// https://github.com/camunda/camunda/issues/51322

		if (['session-invalid', 'session-expired'].includes(this.status)) {
			return;
		}

		if (this.status === 'initial') {
			this.setStatus('session-invalid');
		} else {
			this.setStatus('session-expired');
		}
	};

	reset = () => {
		this.status = DEFAULT_STATUS;
	};
}

const authenticationStore = new Authentication();

export {authenticationStore};
export type {Status};
