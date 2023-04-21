/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {useLocation} from 'react-router-dom';
import {observer} from 'mobx-react';
import {decisionInstancesStore} from 'modules/stores/decisionInstances';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';

import {PanelHeader} from 'modules/components/PanelHeader';
import {SortableTable} from 'modules/components/SortableTable';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/routes';
import {formatDate} from 'modules/utils/date';
import {useFilters} from 'modules/hooks/useFilters';

import {
  Container,
  DecisionContainer,
  CircleBlock,
  DecisionBlock,
  State,
} from './styled';
import {tracking} from 'modules/tracking';

const ROW_HEIGHT = 37;

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
  const {
    state: {status: groupedDecisionsStatus, decisions},
  } = groupedDecisionsStore;

  const location = useLocation();
  const filters = useFilters();

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const decisionId = params.get('name');

    if (groupedDecisionsStatus === 'fetched') {
      if (
        decisionId !== null &&
        !groupedDecisionsStore.isSelectedDecisionValid(decisions, decisionId)
      ) {
        return;
      }

      decisionInstancesStore.fetchDecisionInstancesFromFilters();
    }
  }, [location.search, groupedDecisionsStatus, decisions]);

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
    return `There are no Instances matching this filter set${
      filters.areDecisionInstanceStatesApplied()
        ? ''
        : '\n To see some results, select at least one Instance state'
    }`;
  };

  return (
    <Container>
      <PanelHeader
        title="Decision Instances"
        count={filteredDecisionInstancesCount}
      />
      <SortableTable
        state={getTableState()}
        headerColumns={[
          {
            content: 'Name',
            sortKey: 'decisionName',
          },
          {
            content: 'Decision Instance Key',
            sortKey: 'id',
          },
          {
            content: 'Version',
            sortKey: 'decisionVersion',
          },
          {
            content: 'Evaluation Date',
            sortKey: 'evaluationDate',
            isDefault: true,
          },
          {
            content: 'Process Instance Key',
            sortKey: 'processInstanceId',
          },
        ]}
        skeletonColumns={[
          {
            variant: 'custom',
            customSkeleton: (
              <DecisionContainer>
                <CircleBlock />
                <DecisionBlock />
              </DecisionContainer>
            ),
          },
          {variant: 'block', width: '162px'},
          {variant: 'block', width: '17px'},
          {variant: 'block', width: '151px'},
          {variant: 'block', width: '162px'},
        ]}
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
            evaluationDate,
            processInstanceId,
          }) => {
            return {
              id,
              ariaLabel: `Instance ${id}`,
              content: [
                {
                  cellContent: (
                    <>
                      <State
                        state={state}
                        data-testid={`${state}-icon-${id}`}
                      />
                      {decisionName}
                    </>
                  ),
                },
                {
                  cellContent: (
                    <Link
                      to={Paths.decisionInstance(id)}
                      title={`View decision instance ${id}`}
                      onClick={() => {
                        tracking.track({
                          eventName: 'navigation',
                          link: 'decision-instances-instance-details',
                        });
                      }}
                    >
                      {id}
                    </Link>
                  ),
                },
                {
                  cellContent: decisionVersion,
                },
                {
                  cellContent: formatDate(evaluationDate),
                },
                {
                  cellContent:
                    processInstanceId !== null ? (
                      <Link
                        to={Paths.processInstance(processInstanceId)}
                        title={`View process instance ${processInstanceId}`}
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
                    ),
                },
              ],
            };
          }
        )}
      />
    </Container>
  );
});

export {InstancesTable};
