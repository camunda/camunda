/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {Decision} from './Decision';
import {PAGE_TITLE} from 'modules/constants';
import {InstancesList} from '../Layout/InstancesList';
import {InstancesTable} from './InstancesTable';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {Filters} from './Filters';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {useLocation, Location} from 'react-router-dom';
import {OperationsPanel} from 'modules/components/OperationsPanel';

type LocationType = Omit<Location, 'state'> & {
  state: {refreshContent?: boolean};
};

const Decisions: React.FC = () => {
  const location = useLocation() as LocationType;

  useEffect(() => {
    document.title = PAGE_TITLE.DECISION_INSTANCES;
    return groupedDecisionsStore.reset;
  }, []);

  useEffect(() => {
    if (
      groupedDecisionsStore.state.status === 'initial' ||
      location.state?.refreshContent
    ) {
      groupedDecisionsStore.fetchDecisions();
    }
  }, [location.state]);

  return (
    <>
      <VisuallyHiddenH1>Operate Decision Instances</VisuallyHiddenH1>
      <InstancesList
        type="decision"
        leftPanel={<Filters />}
        topPanel={<Decision />}
        rightPanel={<OperationsPanel />}
        bottomPanel={<InstancesTable />}
      />
    </>
  );
};

export {Decisions};
