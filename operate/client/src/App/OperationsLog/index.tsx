/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {useLocation, type Location} from 'react-router-dom';
import {PAGE_TITLE} from 'modules/constants';
import {Filters} from './Filters';
import {InstancesTable} from './InstancesTable';
import {Container} from './styled';
import {observer} from 'mobx-react';
import {useQueryClient} from '@tanstack/react-query';
import {queryKeys} from 'modules/queries/queryKeys';

const OperationsLog: React.FC = observer(() => {
  const location = useLocation() as Location<{refreshContent?: boolean}>;
  const client = useQueryClient();

  useEffect(() => {
    if (location.state?.refreshContent) {
      client.refetchQueries({
        queryKey: queryKeys.processDefinitions.search(),
        type: 'active',
      });
    }
  }, [location.state, client]);

  useEffect(() => {
    document.title = PAGE_TITLE.AUDIT_LOG;
  }, []);

  return (
    <Container>
      <Filters />
      <InstancesTable />
    </Container>
  );
});

export {OperationsLog};
