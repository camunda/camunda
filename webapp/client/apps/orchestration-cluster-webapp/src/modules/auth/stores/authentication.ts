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

type Status =
	| 'initial'
	| 'logged-in'
	| 'logged-out'
	| 'session-expired'
	| 'session-invalid'
	// TODO: handle invalid-third-party-session once getClientConfig is supported https://github.com/camunda/camunda/issues/51322
	| 'invalid-third-party-session';

const DEFAULT_STATUS: Status = 'initial';

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
		const {error} = await request(endpoints.login({username, password}), {
			skipSessionCheck: true,
		});

		return {error};
	};

	setStatus = (status: Status) => {
		this.status = status;
	};

	handleLogout = async () => {
		const {error} = await request(endpoints.logout(), {
			skipSessionCheck: true,
		});

		if (error !== null) {
			return error;
		}

		reactQueryClient.clear();

		// TODO: handle IdP RP-initiated logout (canLogout / isLoginDelegated) once getClientConfig is supported
		// https://github.com/camunda/camunda/issues/51322

		this.setStatus('logged-out');
		return;
	};

	activateSession = () => {
		this.setStatus('logged-in');
	};

	disableSession = () => {
		// TODO: handle third-party session expiration (canLogout / isLoginDelegated) once getClientConfig is supported
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
