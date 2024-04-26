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
import {getStage} from './getStage';

const EVENT_PREFIX = 'operate:';

/**
 * These are all available events for mixpanel tracking. If a new event is introduced,
 * it needs to be added here first.
 */
type Events =
  | {
      eventName: 'navigation';
      link:
        | 'header-logo'
        | 'header-dashboard'
        | 'header-processes'
        | 'header-decisions'
        | 'dashboard-running-processes'
        | 'dashboard-processes-with-incidents'
        | 'dashboard-active-processes'
        | 'dashboard-process-instances-by-name-all-versions'
        | 'dashboard-process-instances-by-name-single-version'
        | 'dashboard-process-incidents-by-error-message-all-processes'
        | 'dashboard-process-incidents-by-error-message-single-version'
        | 'processes-instance-details'
        | 'processes-parent-instance-details'
        | 'process-details-parent-details'
        | 'process-details-breadcrumb'
        | 'process-details-called-instances'
        | 'process-details-version'
        | 'decision-instances-instance-details'
        | 'decision-instances-parent-process-details'
        | 'decision-details-parent-process-details'
        | 'decision-details-version';
      currentPage?:
        | 'dashboard'
        | 'processes'
        | 'decisions'
        | 'process-details'
        | 'decision-details'
        | 'login';
    }
  | {
      eventName: 'theme-toggle';
      toggledTo: 'light' | 'dark' | 'system';
    }
  | {
      eventName: 'batch-operation';
      operationType: OperationEntityType;
    }
  | {
      eventName: 'single-operation';
      operationType: OperationEntityType;
      source: 'instances-list' | 'incident-table' | 'instance-header';
    }
  | {
      eventName: 'instances-loaded';
      filters: string[];
      sortBy?: string;
      sortOrder?: 'desc' | 'asc';
    }
  | {
      eventName: 'decisions-loaded';
      filters: string[];
      sortBy?: string;
      sortOrder?: 'desc' | 'asc';
    }
  | {
      eventName: 'variables-panel-used';
      toTab: string;
    }
  | {
      eventName: 'drd-panel-interaction';
      action: 'open' | 'close' | 'maximize' | 'minimize';
    }
  | {
      eventName: 'operate-loaded';
      theme: 'dark' | 'light' | 'system';
    }
  | {
      eventName: 'process-instance-details-loaded';
      state: InstanceEntityState;
    }
  | {
      eventName: 'decision-instance-details-loaded';
      state: DecisionInstanceEntityState;
    }
  | {
      eventName: 'incidents-panel-opened';
    }
  | {
      eventName: 'incidents-panel-closed';
    }
  | {
      eventName: 'incidents-sorted';
      column: string;
    }
  | {
      eventName: 'incidents-panel-full-error-message-opened';
    }
  | {
      eventName: 'incident-filtered';
    }
  | {
      eventName: 'incident-filters-cleared';
    }
  | {
      eventName: 'metadata-popover-opened';
    }
  | {
      eventName: 'metadata-popover-closed';
    }
  | {
      eventName: 'diagram-zoom-in';
    }
  | {
      eventName: 'diagram-zoom-out';
    }
  | {
      eventName: 'diagram-zoom-reset';
    }
  | {
      eventName: 'flow-node-instance-details-opened';
    }
  | {
      eventName: 'flow-node-incident-details-opened';
    }
  | {
      eventName: 'json-editor-opened';
      variant:
        | 'add-variable'
        | 'edit-variable'
        | 'search-variable'
        | 'search-multiple-variables';
    }
  | {
      eventName: 'json-editor-closed';
      variant:
        | 'add-variable'
        | 'edit-variable'
        | 'search-variable'
        | 'search-multiple-variables';
    }
  | {
      eventName: 'json-editor-saved';
      variant:
        | 'add-variable'
        | 'edit-variable'
        | 'search-variable'
        | 'search-multiple-variables';
    }
  | {
      eventName: 'instance-history-end-time-toggled';
    }
  | {
      eventName: 'instance-history-item-clicked';
    }
  | {
      eventName: 'enable-modification-mode';
    }
  | {
      eventName: 'apply-modifications-summary';
      hasPendingModifications: boolean;
    }
  | {
      eventName: 'apply-modifications';
      isProcessCanceled: boolean;
      addToken: number;
      cancelToken: number;
      moveToken: number;
      addVariable: number;
      editVariable: number;
    }
  | {
      eventName: 'discard-all-summary';
      hasPendingModifications: boolean;
    }
  | {
      eventName: 'discard-modifications';
      hasPendingModifications: boolean;
    }
  | {
      eventName: 'undo-modification';
      modificationType:
        | 'ADD_TOKEN'
        | 'CANCEL_TOKEN'
        | 'MOVE_TOKEN'
        | 'ADD_VARIABLE'
        | 'EDIT_VARIABLE';
    }
  | {
      eventName: 'add-token';
    }
  | {
      eventName: 'cancel-token';
    }
  | {
      eventName: 'move-token';
    }
  | {
      eventName: 'modification-successful';
    }
  | {
      eventName: 'modification-failed';
    }
  | {
      eventName: 'leave-modification-mode';
    }
  | {
      eventName: 'app-switcher-item-clicked';
      app: string;
    }
  | {
      eventName: 'user-side-bar';
      link: 'cookies' | 'terms-conditions' | 'privacy-policy' | 'imprint';
    }
  | {
      eventName: 'info-bar';
      link: 'documentation' | 'academy' | 'feedback' | 'slack';
    }
  | {
      eventName: 'dashboard-link-clicked';
      link: 'modeler' | 'operate-docs';
    }
  | {
      eventName: 'date-range-popover-opened';
      filterName: string;
    }
  | {
      eventName: 'date-range-applied';
      filterName: string;
      methods: {
        datePicker: boolean;
        dateInput: boolean;
        timeInput: boolean;
        quickFilter: boolean;
      };
    }
  | {
      eventName: 'optional-filter-selected';
      filterName: string;
    }
  | {
      eventName: 'process-instances-filtered';
      filterName: 'variable';
      multipleValues: boolean;
    }
  | {
      eventName: 'definition-deletion-button';
      resource: 'process' | 'decision';
      version: string;
    }
  | {
      eventName: 'definition-deletion-confirmation';
      resource: 'process' | 'decision';
      version: string;
    }
  | {
      eventName: 'open-tasklist-link-clicked';
    }
  /**
   * Process instance migration
   */
  | {
      eventName: 'process-instance-migration-button-clicked';
    }
  | {
      eventName: 'process-instance-migration-mode-entered';
    }
  | {
      eventName: 'process-instance-migration-confirmed';
    }
  /**
   * Process instance batch modification
   */
  | {
      eventName: 'batch-move-modification-move-button-clicked';
    }
  | {
      eventName: 'batch-move-modification-exit-button-clicked';
    }
  | {
      eventName: 'batch-move-modification-apply-button-clicked';
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
    version: process.env.REACT_APP_VERSION,
  } as const;

  #isTrackingSupported = () => {
    return (
      process.env.NODE_ENV !== 'development' &&
      ['prod', 'int'].includes(STAGE_ENV) &&
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

  identifyUser = (user: {
    userId: string;
    salesPlanType: string | null;
    roles: ReadonlyArray<string> | null;
  }) => {
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
          process.env.REACT_APP_MIXPANEL_TOKEN,
        {
          api_host:
            window.clientConfig?.mixpanelAPIHost ??
            process.env.REACT_MIXPANEL_HOST,
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
      if (STAGE_ENV === 'int') {
        return injectScript(process.env.REACT_APP_OSANO_INT_ENV_URL).then(
          resolve,
        );
      }

      if (STAGE_ENV === 'prod') {
        return injectScript(process.env.REACT_APP_OSANO_PROD_ENV_URL).then(
          resolve,
        );
      }

      return resolve();
    });
  };

  #loadAppCues = (): Promise<void> => {
    return new Promise((resolve) => {
      return injectScript(process.env.REACT_APP_CUES_HOST).then(() => {
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
