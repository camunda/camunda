/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {Decision} from './Decision';
import {PAGE_TITLE} from 'modules/constants';
import {InstancesList} from '../Layout/InstancesList';
import {InstancesTable} from './InstancesTable';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {Filters} from './Filters';
import {useLocation, type Location} from 'react-router-dom';
import {useQueryClient} from '@tanstack/react-query';
import {queryKeys} from 'modules/queries/queryKeys';

const Decisions: React.FC = () => {
  const location = useLocation() as Location<{refreshContent?: boolean}>;
  const client = useQueryClient();

  useEffect(() => {
    document.title = PAGE_TITLE.DECISION_INSTANCES;
  }, []);

  useEffect(() => {
    if (location.state?.refreshContent) {
      client.refetchQueries({
        queryKey: queryKeys.decisionDefinitions.search(),
        type: 'active',
      });
      client.refetchQueries({
        queryKey: queryKeys.decisionInstances.searchPaginated(),
        type: 'active',
      });
    }
  }, [location.state, client]);

  return (
    <>
      <VisuallyHiddenH1>Operate Decision Instances</VisuallyHiddenH1>
      <InstancesList
        type="decision"
        leftPanel={<Filters />}
        topPanel={<Decision />}
        bottomPanel={<InstancesTable />}
      />
    </>
  );
};

export {Decisions};
