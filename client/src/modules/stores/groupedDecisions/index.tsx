/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
  status: 'initial' | 'fetching' | 'fetched' | 'error';
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
      decisionVersionsById: computed,
      decisionVersionsByKey: computed,
    });
  }

  fetchDecisions = this.retryOnConnectionLost(async () => {
    this.startFetching();
    const {name, tenant} = getDecisionInstanceFilters(getSearchString());

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
    this.state.status = 'fetching';
  };

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
      .map(({decisionId, name}) => ({
        value: decisionId,
        label: name ?? decisionId,
      }))
      .sort(sortOptions);
  }

  get decisionVersionsById() {
    return this.state.decisions.reduce<{
      [decisionId: string]: DecisionDto['decisions'];
    }>((decisions, decision) => {
      return {
        ...decisions,
        [decision.decisionId]: [...decision.decisions].sort(
          (decisionA, decisionB) => decisionA.version - decisionB.version,
        ),
      };
    }, {});
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

  getVersions = (decisionId: string) => {
    return (
      this.decisionVersionsById[decisionId]?.map(({version}) => version) ?? []
    );
  };

  getDefaultVersion = (decisionId: string) => {
    const versions = this.getVersions(decisionId);
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
