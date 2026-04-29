/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Mixpanel} from 'mixpanel-browser';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10/authentication';
import {getStage} from '../config/getStage';

type Events =
	| {
			eventName:
				| 'tasklist:task-unassigned'
				| 'tasklist:task-assigned'
				| 'tasklist:task-unassignment-delayed-notification'
				| 'tasklist:task-completion-delayed-notification'
				| 'tasklist:task-completion-rejected-notification'
				| 'tasklist:processes-consent-refused'
				| 'tasklist:processes-consent-accepted'
				| 'tasklist:processes-fetch-failed'
				| 'tasklist:processes-empty-message-link-clicked'
				| 'tasklist:process-start-clicked'
				| 'tasklist:process-started'
				| 'tasklist:process-start-failed'
				| 'tasklist:process-task-toast-clicked'
				| 'tasklist:public-start-form-opened'
				| 'tasklist:public-start-form-loaded'
				| 'tasklist:public-start-form-load-failed'
				| 'tasklist:public-start-form-submitted'
				| 'tasklist:public-start-form-submission-failed'
				| 'tasklist:public-start-form-invalid-form-schema'
				| 'tasklist:os-notification-opted-out'
				| 'tasklist:custom-filter-saved'
				| 'tasklist:custom-filter-applied'
				| 'tasklist:custom-filter-updated'
				| 'tasklist:custom-filter-deleted'
				| 'tasklist:public-start-form-schema-with-file-components'
				| 'tasklist:public-start-form-v2-api-not-supported';
	  }
	| {
			eventName: 'tasklist:task-opened';
			by?: 'user' | 'auto-select';
			position?: number;
			filter?: string;
			sorting?: 'creation' | 'follow-up' | 'due' | 'completion' | 'priority';
	  }
	| {
			eventName: 'tasklist:task-empty-page-opened';
			by?: 'os-notification';
	  }
	| {
			eventName: 'tasklist:task-completed';
			isCamundaForm: boolean;
			hasRemainingTasks: boolean;
			filter: string;
			customFilters: string[];
			customFilterVariableCount: number;
	  }
	| {
			eventName: 'tasklist:tasks-filtered';
			filter: string;
			sorting: 'creation' | 'follow-up' | 'due' | 'completion' | 'priority';
			customFilters: string[];
			customFilterVariableCount: number;
	  }
	| {
			eventName: 'tasklist:navigation';
			link: 'header-logo' | 'header-tasks' | 'header-processes';
	  }
	| {
			eventName: 'tasklist:app-switcher-item-clicked';
			app: string;
	  }
	| {
			eventName: 'tasklist:info-bar';
			link: 'documentation' | 'academy' | 'feedback' | 'forum';
	  }
	| {
			eventName: 'tasklist:user-side-bar';
			link: 'cookies' | 'terms-conditions' | 'privacy-policy' | 'imprint';
	  }
	| {
			eventName: 'tasklist:processes-loaded';
			count: number;
			filter: string;
	  }
	| {
			eventName: 'tasklist:process-tasks-polling-ended';
			outcome: 'single-task-found' | 'multiple-tasks-found' | 'no-tasks-found' | 'navigated-away';
	  }
	| {
			eventName: 'tasklist:app-loaded';
			osNotificationPermission: NotificationPermission;
	  }
	| {
			eventName: 'tasklist:os-notification-permission-requested';
			outcome: NotificationPermission;
	  };

const STAGE_ENV = getStage(window.location.host);

function injectScript(src: string): Promise<void> {
	return new Promise((resolve) => {
		const scriptElement = document.createElement('script');

		scriptElement.src = src;

		document.head.appendChild(scriptElement);

		setTimeout(resolve, 1000);

		scriptElement.onload = () => {
			resolve();
		};
		scriptElement.onerror = () => {
			resolve();
		};
	});
}

class Tracking {
	#mixpanel: null | Mixpanel = null;

	#baseProperties = {
		// TODO: add organizationId and clusterId once available:
		// organizationId: getClientConfig().organizationId,
		// clusterId: getClientConfig().clusterId,
		stage: STAGE_ENV,
		version: import.meta.env.VITE_VERSION,
	} as const;

	#isTrackingSupported = () => {
		return (
			!import.meta.env.DEV && ['prod', 'int', 'dev'].includes(STAGE_ENV)
			// TODO: add organizationId check once available
			// && getClientConfig().organizationId
		);
	};

	track = (events: Events) => {
		if (!this.#isTrackingSupported() || !this.#isTrackingAllowed()) {
			return;
		}

		if (this.#mixpanel === null) {
			console.warn('Could not track event because mixpanel was not properly loaded.');
		}

		const {eventName, ...properties} = events;

		try {
			this.#mixpanel?.track(eventName, properties);
		} catch (error) {
			console.error(`Can't track event: ${eventName}`, error);
		}
	};

	identifyUser = (user: CurrentUser) => {
		this.#mixpanel?.identify(user.username);
	};

	#isTrackingAllowed = () => {
		return Boolean(window.Osano?.cm?.analytics);
	};

	#loadMixpanel = async (): Promise<void> => {
		return import('mixpanel-browser').then(({default: mixpanel}) => {
			mixpanel.init(import.meta.env.VITE_MIXPANEL_TOKEN, {
				api_host: import.meta.env.VITE_MIXPANEL_HOST,
				opt_out_tracking_by_default: true,
			});
			// TODO: add mixpanelToken and mixpanelHost to client config once available
			// mixpanel.init(
			// getClientConfig().mixpanelToken ?? import.meta.env.VITE_MIXPANEL_TOKEN,
			// {
			//   api_host:
			//     getClientConfig().mixpanelAPIHost ??
			//     import.meta.env.VITE_MIXPANEL_HOST,
			//   opt_out_tracking_by_default: true,
			// },
			//   );
			mixpanel.register(this.#baseProperties);
			this.#mixpanel = mixpanel;
			window.mixpanel = mixpanel;
		});
	};

	#loadOsano = async (): Promise<void> => {
		return new Promise((resolve) => {
			if (STAGE_ENV === 'dev') {
				return injectScript(import.meta.env.VITE_OSANO_DEV_ENV_URL).then(resolve);
			}

			if (STAGE_ENV === 'int') {
				return injectScript(import.meta.env.VITE_OSANO_INT_ENV_URL).then(resolve);
			}

			if (STAGE_ENV === 'prod') {
				return injectScript(import.meta.env.VITE_OSANO_PROD_ENV_URL).then(resolve);
			}

			return resolve();
		});
	};

	loadAnalyticsToWillingUsers = async (): Promise<void> => {
		if (!this.#isTrackingSupported()) {
			console.warn('Tracking is not supported for this environment');
			return;
		}

		await this.#loadOsano();

		const analyticsConsented = window.Osano?.cm?.analytics === true;

		await this.#loadMixpanel();

		if (analyticsConsented) {
			this.#mixpanel?.opt_in_tracking();
		}

		window.Osano?.cm?.addEventListener('osano-cm-consent-saved', ({ANALYTICS}) => {
			if (ANALYTICS === 'ACCEPT') {
				this.#mixpanel?.opt_in_tracking();
			}
			if (ANALYTICS === 'DENY') {
				this.#mixpanel?.opt_out_tracking();
			}
		});
	};
}

const tracking = new Tracking();

export {tracking};
