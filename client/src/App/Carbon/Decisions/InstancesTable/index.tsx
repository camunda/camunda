/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {PanelHeader} from 'modules/components/Carbon/PanelHeader';
import {SortableTable} from 'modules/components/Carbon/SortableTable';
import {StateIcon} from 'modules/components/Carbon/StateIcon';
import {decisionInstancesStore} from 'modules/stores/decisionInstances';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {formatDate} from 'modules/utils/date';
import {useEffect} from 'react';
import {useLocation} from 'react-router-dom';
import {Container, DecisionName} from './styled';
import {observer} from 'mobx-react';
import {CarbonPaths} from 'modules/carbonRoutes';
import {tracking} from 'modules/tracking';
import {Link} from 'modules/components/Carbon/Link';

const ROW_HEIGHT = 34;

const InstancesTable: React.FC = observer(() => {
  const {
    state: {filteredDecisionInstancesCount, latestFetch, decisionInstances},
    hasLatestDecisionInstances,
  } = decisionInstancesStore;

  const location = useLocation();

  const {
    state: {status: groupedDecisionsStatus, decisions},
  } = groupedDecisionsStore;

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

  return (
    <Container>
      <PanelHeader
        title="Decision Instances"
        count={filteredDecisionInstancesCount}
      />
      <SortableTable
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
                  to={CarbonPaths.decisionInstance(id)}
                  onClick={() => {
                    tracking.track({
                      eventName: 'navigation',
                      link: 'decision-instances-parent-process-details',
                    });
                  }}
                  title={`View decision instance ${id}`}
                >
                  {id}
                </Link>
              ),
              decisionVersion,
              evaluationDate: formatDate(evaluationDate),
              processInstanceId: (
                <>
                  {processInstanceId !== null ? (
                    <Link
                      to={CarbonPaths.processInstance(processInstanceId)}
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
                  )}
                </>
              ),
            };
          }
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
