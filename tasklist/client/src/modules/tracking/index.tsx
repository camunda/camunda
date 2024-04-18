/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {Mixpanel} from 'mixpanel-browser';
import {getStage} from 'modules/utils/getStage';
import {CurrentUser} from 'modules/types';
import {TaskFilters} from 'modules/hooks/useTaskFilters';

const EVENT_PREFIX = 'tasklist:';
type Events =
  | {
      eventName:
        | 'task-unassigned'
        | 'task-assigned'
        | 'processes-consent-refused'
        | 'processes-consent-accepted'
        | 'processes-fetch-failed'
        | 'processes-empty-message-link-clicked'
        | 'process-start-clicked'
        | 'process-started'
        | 'process-start-failed'
        | 'process-task-toast-clicked'
        | 'public-start-form-opened'
        | 'public-start-form-loaded'
        | 'public-start-form-load-failed'
        | 'public-start-form-submitted'
        | 'public-start-form-submission-failed'
        | 'public-start-form-invalid-form-schema'
        | 'os-notification-opted-out';
    }
  | {
      eventName: 'task-opened';
      by?: 'user' | 'auto-select';
      position?: number;
      filter?: TaskFilters['filter'];
      sorting?: TaskFilters['sortBy'];
    }
  | {
      eventName: 'task-empty-page-opened';
      by?: 'os-notification';
    }
  | {
      eventName: 'task-completed';
      isCamundaForm: boolean;
      hasRemainingTasks: boolean;
      filter: TaskFilters['filter'];
      customFilters: string[];
      customFilterVariableCount: number;
    }
  | {
      eventName: 'tasks-filtered';
      filter: TaskFilters['filter'];
      sorting: TaskFilters['sortBy'];
      customFilters: string[];
      customFilterVariableCount: number;
    }
  | {
      eventName: 'navigation';
      link: 'header-logo' | 'header-tasks' | 'header-processes';
    }
  | {
      eventName: 'app-switcher-item-clicked';
      app: string;
    }
  | {
      eventName: 'info-bar';
      link: 'documentation' | 'academy' | 'feedback' | 'forum';
    }
  | {
      eventName: 'user-side-bar';
      link: 'cookies' | 'terms-conditions' | 'privacy-policy' | 'imprint';
    }
  | {
      eventName: 'processes-loaded';
      count: number;
      filter: string;
    }
  | {
      eventName: 'process-tasks-polling-ended';
      outcome:
        | 'single-task-found'
        | 'multiple-tasks-found'
        | 'no-tasks-found'
        | 'navigated-away';
    }
  | {
      eventName: 'app-loaded';
      osNotificationPermission: NotificationPermission;
    }
  | {
      eventName: 'os-notification-permission-requested';
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
  #appCues: null | NonNullable<typeof window.Appcues> = null;

  #baseProperties = {
    organizationId: window.clientConfig?.organizationId,
    clusterId: window.clientConfig?.clusterId,
    stage: STAGE_ENV,
    version: import.meta.env.VITE_VERSION,
  } as const;

  #isTrackingSupported = () => {
    return (
      !import.meta.env.DEV &&
      ['prod', 'int', 'dev'].includes(STAGE_ENV) &&
      window.clientConfig?.organizationId
    );
  };

  track(events: Events) {
    if (!this.#isTrackingSupported() || !this.#isTrackingAllowed()) {
      return;
    }

    if (this.#mixpanel === null) {
      console.warn(
        'Could not track event because mixpanel was not properly loaded.',
      );
    }

    const {eventName, ...properties} = events;
    const prefixedEventName = `${EVENT_PREFIX}${eventName}`;

    try {
      this.#mixpanel?.track(prefixedEventName, properties);
      this.#appCues?.track(prefixedEventName, {
        ...this.#baseProperties,
        ...properties,
      });
    } catch (error) {
      console.error(`Can't track event: ${eventName}`, error);
    }
  }

  identifyUser = (user: CurrentUser) => {
    this.#mixpanel?.identify(user.userId);
    this.#appCues?.identify(user.userId, {
      orgId: this.#baseProperties.organizationId,
      salesPlanType: user.salesPlanType ?? '',
      roles: user.roles?.join('|'),
      clusters: this.#baseProperties.clusterId,
    });
  };

  trackPagination = () => {
    this.#appCues?.page();
  };

  #isTrackingAllowed = () => {
    return Boolean(window.Osano?.cm?.analytics);
  };

  #loadMixpanel = (): Promise<void> => {
    return import('mixpanel-browser').then(({default: mixpanel}) => {
      mixpanel.init(
        window.clientConfig?.mixpanelToken ??
          import.meta.env.VITE_MIXPANEL_TOKEN,
        {
          api_host:
            window.clientConfig?.mixpanelAPIHost ??
            import.meta.env.VITE_MIXPANEL_HOST,
          opt_out_tracking_by_default: true,
        },
      );
      mixpanel.register(this.#baseProperties);
      this.#mixpanel = mixpanel;
      window.mixpanel = mixpanel;
    });
  };

  #loadOsano = (): Promise<void> => {
    return new Promise((resolve) => {
      if (STAGE_ENV === 'dev') {
        return injectScript(import.meta.env.VITE_OSANO_DEV_ENV_URL).then(
          resolve,
        );
      }

      if (STAGE_ENV === 'int') {
        return injectScript(import.meta.env.VITE_OSANO_INT_ENV_URL).then(
          resolve,
        );
      }

      if (STAGE_ENV === 'prod') {
        return injectScript(import.meta.env.VITE_OSANO_PROD_ENV_URL).then(
          resolve,
        );
      }

      return resolve();
    });
  };

  #loadAppCues = (): Promise<void> => {
    return new Promise((resolve) => {
      return injectScript(import.meta.env.VITE_CUES_HOST).then(() => {
        if (window.Appcues) {
          this.#appCues = window.Appcues;
        }

        resolve();
      });
    });
  };

  loadAnalyticsToWillingUsers(): Promise<void[] | void> {
    if (!this.#isTrackingSupported()) {
      console.warn('Tracking is not supported for this environment');
      return Promise.resolve();
    }

    return this.#loadOsano().then(() =>
      Promise.all([this.#loadMixpanel(), this.#loadAppCues()]).then(() => {
        window.Osano?.cm?.addEventListener(
          'osano-cm-consent-saved',
          ({ANALYTICS}) => {
            if (ANALYTICS === 'ACCEPT') {
              this.#mixpanel?.opt_in_tracking();
            }
            if (ANALYTICS === 'DENY') {
              this.#mixpanel?.opt_out_tracking();
            }
          },
        );
      }),
    );
  }
}

const tracking = new Tracking();

export {tracking};
