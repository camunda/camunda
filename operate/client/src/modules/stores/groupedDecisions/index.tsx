/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import {
  action,
  computed,
  IReactionDisposer,
  makeObservable,
  observable,
  override,
} from 'mobx';
import {
  fetchGroupedDecisions,
  DecisionDto,
} from 'modules/api/decisions/fetchGroupedDecisions';
import {sortOptions} from 'modules/utils/sortOptions';
import {NetworkReconnectionHandler} from '../networkReconnectionHandler';
import {getSearchString} from 'modules/utils/getSearchString';
import {getDecisionInstanceFilters} from 'modules/utils/filter';
import {DEFAULT_TENANT, PERMISSIONS} from 'modules/constants';

type Decision = DecisionDto & {key: string};
type State = {
  decisions: Decision[];
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  decisions: [],
  status: 'initial',
};

const generateDecisionKey = (decisionId: string, tenantId?: string | null) => {
  return `{${decisionId}}-{${tenantId ?? DEFAULT_TENANT}}`;
};

class GroupedDecisions extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  disposer: IReactionDisposer | null = null;
  retryCount: number = 0;
  retryDecisionsFetchTimeout: NodeJS.Timeout | null = null;

  constructor() {
    super();

    makeObservable(this, {
      state: observable,
      handleFetchSuccess: action,
      handleFetchFailure: action,
      startFetching: action,
      reset: override,
      decisions: computed,
      decisionVersionsByKey: computed,
      isInitialLoadComplete: computed,
    });
  }

  fetchDecisions = this.retryOnConnectionLost(async (tenantId?: string) => {
    this.startFetching();
    const {name, tenant: tenantFromURL} =
      getDecisionInstanceFilters(getSearchString());

    const tenant = tenantId ?? tenantFromURL;

    const response = await fetchGroupedDecisions(
      tenant === 'all' ? undefined : tenant,
    );

    if (response.isSuccess) {
      const decisions = response.data;
      if (
        name !== undefined &&
        decisions.find(
          (decision) =>
            decision.decisionId === name &&
            decision.tenantId === (tenant ?? DEFAULT_TENANT),
        ) === undefined
      ) {
        this.handleRefetch(decisions);
      } else {
        this.resetRetryDecisionsFetch();
        this.handleFetchSuccess(decisions);
      }
    } else {
      this.handleFetchFailure();
    }
  });

  startFetching = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  get isInitialLoadComplete() {
    if (['initial', 'first-fetch'].includes(this.state.status)) {
      return false;
    }

    return this.state.status !== 'fetching' || this.retryCount === 0;
  }

  handleRefetch = (decisions: DecisionDto[]) => {
    if (this.retryCount < 3) {
      this.retryCount += 1;

      this.retryDecisionsFetchTimeout = setTimeout(() => {
        this.fetchDecisions();
      }, 5000);
    } else {
      this.resetRetryDecisionsFetch();
      this.handleFetchSuccess(decisions);
    }
  };

  handleFetchSuccess = (decisions: DecisionDto[]) => {
    this.state.decisions = decisions.map((decision) => {
      return {
        key: generateDecisionKey(decision.decisionId, decision.tenantId),
        ...decision,
      };
    });
    this.state.status = 'fetched';
  };

  handleFetchFailure = () => {
    this.state.status = 'error';
  };

  get decisions() {
    return this.state.decisions
      .map(({key, tenantId, decisionId, name}) => ({
        id: key,
        decisionId,
        label: name ?? decisionId,
        tenantId,
      }))
      .sort(sortOptions);
  }

  get decisionVersionsByKey() {
    return this.state.decisions.reduce<{
      [key: string]: DecisionDto['decisions'];
    }>((decisionVersions, decision) => {
      return {
        ...decisionVersions,
        [decision.key]: [...decision.decisions].sort(
          (decisionA, decisionB) => decisionA.version - decisionB.version,
        ),
      };
    }, {});
  }

  isSelectedDecisionValid = ({
    decisionId,
    tenantId,
  }: {
    decisionId: string;
    tenantId?: string | null;
  }) => {
    return this.getDecision(decisionId, tenantId) !== undefined;
  };

  getDecisionName = ({
    decisionId,
    tenantId,
  }: {
    decisionId: string | null;
    tenantId?: string | null;
  }) => {
    const decision = this.getDecision(decisionId, tenantId);

    return decision?.name ?? decision?.decisionId;
  };

  getDecisionDefinitionId = ({
    decisionId,
    tenantId,
    version,
  }: {
    decisionId: string;
    tenantId?: string | null;
    version: number;
  }) => {
    return (
      this.decisionVersionsByKey[
        generateDecisionKey(decisionId, tenantId)
      ]?.find((decision) => decision.version === version)?.id ?? null
    );
  };

  getVersions = (decisionKey: string) => {
    return (
      this.decisionVersionsByKey[decisionKey]?.map(({version}) => version) ?? []
    );
  };

  getDefaultVersion = (decisionKey: string) => {
    const versions = this.getVersions(decisionKey);
    return versions[versions.length - 1];
  };

  get areDecisionsEmpty() {
    return this.state.decisions.length === 0;
  }

  resetRetryDecisionsFetch = () => {
    if (this.retryDecisionsFetchTimeout !== null) {
      clearTimeout(this.retryDecisionsFetchTimeout);
    }

    this.retryCount = 0;
  };

  getDecision = (decisionId?: string | null, tenant?: string | null) => {
    return this.state.decisions.find(
      (decision) =>
        decision.decisionId === decisionId &&
        decision.tenantId === (tenant ?? DEFAULT_TENANT),
    );
  };

  getPermissions = (decisionId?: string, tenant?: string | null) => {
    if (!window.clientConfig?.resourcePermissionsEnabled) {
      return PERMISSIONS;
    }

    if (decisionId === undefined) {
      return [];
    }

    return this.getDecision(decisionId, tenant)?.permissions;
  };

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};
  }
}

export const groupedDecisionsStore = new GroupedDecisions();
export {generateDecisionKey};
