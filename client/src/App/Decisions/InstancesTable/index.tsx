/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import {useLocation} from 'react-router-dom';
import {observer} from 'mobx-react';
import {decisionInstancesStore} from 'modules/stores/decisionInstances';

import {Panel} from 'modules/components/Panel';
import {PanelHeader} from 'modules/components/PanelHeader';
import {SortableTable} from 'modules/components/SortableTable';
import {Link} from 'modules/components/Link';
import {Locations} from 'modules/routes';
import {formatDate} from 'modules/utils/date';

import {
  Container,
  DecisionContainer,
  CircleBlock,
  DecisionBlock,
  Copyright,
  State,
} from './styled';

const ROW_HEIGHT = 37;

const InstancesTable: React.FC = observer(() => {
  const {
    state: {status, filteredInstancesCount, latestFetch, decisionInstances},
    areDecisionInstancesEmpty,
    hasLatestDecisionInstances,
  } = decisionInstancesStore;
  const location = useLocation();

  useEffect(() => {
    decisionInstancesStore.fetchInstancesFromFilters();
  }, [location.search]);

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
    let message = 'There are no Instances matching this filter set';

    //TODO: If filters are applied, append '\n To see some results, select at least one Instance state';

    return message;
  };

  return (
    <Container>
      <PanelHeader title="Instances" count={filteredInstancesCount} />
      <SortableTable
        state={getTableState()}
        headerColumns={[
          {
            content: 'Decision',
            sortKey: 'decision',
          },
          {
            content: 'Decision Instance Id',
            sortKey: 'decisionInstanceId',
          },
          {
            content: 'Version',
            sortKey: 'version',
          },
          {
            content: 'Evaluation Date',
            sortKey: 'evaluationTime',
            isDefault: true,
          },
          {
            content: 'Process Instance Id',
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
          ({id, state, name, version, evaluationTime, processInstanceId}) => {
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
                      {name}
                    </>
                  ),
                },
                {
                  cellContent: (
                    <Link
                      to={Locations.decisionInstance(location, id)}
                      title={`View decision instance ${id}`}
                    >
                      {id}
                    </Link>
                  ),
                },
                {
                  cellContent: version,
                },
                {
                  cellContent: formatDate(evaluationTime),
                },
                {
                  cellContent:
                    processInstanceId !== null ? (
                      <Link
                        to={Locations.instance(location, processInstanceId)}
                        title={`View process instance ${processInstanceId}`}
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
      <Panel.Footer>
        <Copyright />
      </Panel.Footer>
    </Container>
  );
});

export {InstancesTable};
