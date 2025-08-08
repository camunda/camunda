/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {PanelHeader} from 'modules/components/PanelHeader';
import {SortableTable} from 'modules/components/SortableTable';
import {StateIcon} from 'modules/components/StateIcon';
import {decisionInstancesStore} from 'modules/stores/decisionInstances';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {formatDate} from 'modules/utils/date';
import {useEffect} from 'react';
import {useLocation} from 'react-router-dom';
import {Container, DecisionName} from './styled';
import {observer} from 'mobx-react';
import {Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {Link} from 'modules/components/Link';
import {useFilters} from 'modules/hooks/useFilters';
import {getDecisionInstanceFilters} from 'modules/utils/filter';

const ROW_HEIGHT = 34;

const InstancesTable: React.FC = observer(() => {
  const {
    state: {
      status,
      filteredDecisionInstancesCount,
      latestFetch,
      decisionInstances,
    },
    areDecisionInstancesEmpty,
    hasLatestDecisionInstances,
  } = decisionInstancesStore;

  const location = useLocation();
  const filters = useFilters();

  const {isInitialLoadComplete} = groupedDecisionsStore;

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const decisionId = params.get('name');
    const tenantId = params.get('tenant');

    if (isInitialLoadComplete && !location.state?.refreshContent) {
      if (
        decisionId !== null &&
        !groupedDecisionsStore.isSelectedDecisionValid({
          decisionId,
          tenantId,
        })
      ) {
        return;
      }

      decisionInstancesStore.fetchDecisionInstancesFromFilters();
    }
  }, [location.search, isInitialLoadComplete, location.state]);

  useEffect(() => {
    if (isInitialLoadComplete && location.state?.refreshContent) {
      decisionInstancesStore.fetchDecisionInstancesFromFilters();
    }
  }, [isInitialLoadComplete, location.state]);

  const getTableState = () => {
    if (['initial', 'first-fetch'].includes(status)) {
      return 'skeleton';
    }
    if (status === 'fetching') {
      return 'loading';
    }
    if (status === 'error') {
      return 'error';
    }
    if (areDecisionInstancesEmpty) {
      return 'empty';
    }

    return 'content';
  };

  const getEmptyListMessage = () => {
    return {
      message: 'There are no Instances matching this filter set',
      additionalInfo: filters.areDecisionInstanceStatesApplied()
        ? undefined
        : 'To see some results, select at least one Instance state',
    };
  };

  const {tenant} = getDecisionInstanceFilters(location.search);

  const isTenantColumnVisible =
    window.clientConfig?.multiTenancyEnabled &&
    (tenant === undefined || tenant === 'all');

  return (
    <Container>
      <PanelHeader title="Decision Instances">
        <span>{filteredDecisionInstancesCount}</span>
      </PanelHeader>
      <SortableTable
        state={getTableState()}
        emptyMessage={getEmptyListMessage()}
        onVerticalScrollStartReach={async (scrollDown) => {
          if (decisionInstancesStore.shouldFetchPreviousInstances() === false) {
            return;
          }

          await decisionInstancesStore.fetchPreviousInstances();

          if (hasLatestDecisionInstances) {
            scrollDown(latestFetch?.decisionInstancesCount ?? 0 * ROW_HEIGHT);
          }
        }}
        onVerticalScrollEndReach={() => {
          if (decisionInstancesStore.shouldFetchNextInstances() === false) {
            return;
          }

          decisionInstancesStore.fetchNextInstances();
        }}
        rows={decisionInstances.map(
          ({
            id,
            state,
            decisionName,
            decisionVersion,
            tenantId,
            evaluationDate,
            processInstanceId,
          }) => {
            return {
              id,
              decisionName: (
                <DecisionName>
                  <StateIcon
                    state={state}
                    data-testid={`${state}-icon-${id}`}
                    size={20}
                  />
                  {decisionName}
                </DecisionName>
              ),
              decisionInstanceKey: (
                <Link
                  to={Paths.decisionInstance(id)}
                  onClick={() => {
                    tracking.track({
                      eventName: 'navigation',
                      link: 'decision-instances-parent-process-details',
                    });
                  }}
                  title={`View decision instance ${id}`}
                  aria-label={`View decision instance ${id}`}
                >
                  {id}
                </Link>
              ),
              decisionVersion,
              tenant: isTenantColumnVisible ? tenantId : undefined,
              evaluationDate: formatDate(evaluationDate),
              processInstanceId: (
                <>
                  {processInstanceId !== null ? (
                    <Link
                      to={Paths.processInstance(processInstanceId)}
                      title={`View process instance ${processInstanceId}`}
                      aria-label={`View process instance ${processInstanceId}`}
                      onClick={() => {
                        tracking.track({
                          eventName: 'navigation',
                          link: 'decision-instances-parent-process-details',
                        });
                      }}
                    >
                      {processInstanceId}
                    </Link>
                  ) : (
                    'None'
                  )}
                </>
              ),
            };
          },
        )}
        headerColumns={[
          {
            header: 'Name',
            key: 'decisionName',
          },
          {
            header: 'Decision Instance Key',
            key: 'decisionInstanceKey',
            sortKey: 'id',
          },
          {
            header: 'Version',
            key: 'decisionVersion',
          },
          ...(isTenantColumnVisible
            ? [
                {
                  header: 'Tenant',
                  key: 'tenant',
                },
              ]
            : []),
          {
            header: 'Evaluation Date',
            key: 'evaluationDate',
            isDefault: true,
          },
          {
            header: 'Process Instance Key',
            key: 'processInstanceId',
          },
        ]}
      />
    </Container>
  );
});

export {InstancesTable};
