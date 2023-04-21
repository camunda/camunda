/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {PanelHeader} from 'modules/components/Carbon/PanelHeader';
import {SortableTable} from 'modules/components/Carbon/SortableTable';
import {decisionInstancesStore} from 'modules/stores/decisionInstances';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {formatDate} from 'modules/utils/date';
import {useEffect} from 'react';
import {useLocation} from 'react-router-dom';
import {Container} from './styled';
import {observer} from 'mobx-react';

const InstancesTable: React.FC = observer(() => {
  const {
    state: {filteredDecisionInstancesCount, decisionInstances},
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
        rows={decisionInstances.map(
          ({
            id,
            decisionName,
            decisionVersion,
            evaluationDate,
            processInstanceId,
          }) => {
            return {
              id,
              decisionName,
              decisionInstanceKey: id,
              decisionVersion,
              evaluationDate: formatDate(evaluationDate),
              processInstanceId: (
                <>{processInstanceId !== null ? processInstanceId : 'None'}</>
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
          },
          {
            header: 'Version',
            key: 'decisionVersion',
          },
          {
            header: 'Evaluation Date',
            key: 'evaluationDate',
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
