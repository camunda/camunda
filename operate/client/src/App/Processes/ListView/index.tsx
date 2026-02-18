/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {InstancesList} from '../../Layout/InstancesList';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {Filters} from './Filters';
import {InstancesTableWrapper} from './InstancesTable/InstancesTableWrapper';
import {DiagramPanel} from './DiagramPanel';
import {observer} from 'mobx-react';
import {useEffect} from 'react';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {useLocation, type Location} from 'react-router-dom';
import {PAGE_TITLE} from 'modules/constants';
import {batchModificationStore} from 'modules/stores/batchModification';
import {ProcessDefinitionKeyContext} from './processDefinitionKeyContext';
import {SelectedProcessDefinitionContext} from './selectedProcessDefinitionContext';
import {useFilters} from 'modules/hooks/useFilters';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {reaction} from 'mobx';
import {tracking} from 'modules/tracking';
import {useSelectedProcessDefinition} from 'modules/hooks/processDefinitions';
import {useQueryClient} from '@tanstack/react-query';
import {queryKeys} from 'modules/queries/queryKeys';

const ListView: React.FC = observer(() => {
  const location = useLocation() as Location<{refreshContent?: boolean}>;
  const client = useQueryClient();

  const {getFilters} = useFilters();
  const filters = getFilters();
  const filtersJSON = JSON.stringify(filters);

  useEffect(() => {
    if (location.state?.refreshContent) {
      client.refetchQueries({
        queryKey: queryKeys.processDefinitions.search(),
        type: 'active',
      });
    }
  }, [location.state, client]);

  useEffect(() => {
    processInstancesSelectionStore.init();

    document.title = PAGE_TITLE.INSTANCES;

    return () => {
      processInstancesSelectionStore.reset();
    };
  }, []);

  useEffect(() => {
    processInstancesSelectionStore.resetState();
  }, [filtersJSON]);

  useEffect(() => {
    const disposer = reaction(
      () => variableFilterStore.state.conditions,
      () => {
        tracking.track({
          eventName: 'process-instances-filtered',
          filterName: 'variable',
          multipleValues: variableFilterStore.state.conditions.length > 1,
        });
      },
    );
    return disposer;
  }, []);

  const {data: selectedProcessDefinition} = useSelectedProcessDefinition();

  return (
    <ProcessDefinitionKeyContext.Provider
      value={selectedProcessDefinition?.processDefinitionKey}
    >
      <SelectedProcessDefinitionContext.Provider
        value={selectedProcessDefinition}
      >
        <VisuallyHiddenH1>Operate Process Instances</VisuallyHiddenH1>
        <InstancesList
          type="process"
          leftPanel={<Filters />}
          topPanel={<DiagramPanel />}
          bottomPanel={<InstancesTableWrapper />}
          frame={{
            isVisible: batchModificationStore.state.isEnabled,
            headerTitle: 'Batch Modification Mode',
          }}
        />
      </SelectedProcessDefinitionContext.Provider>
    </ProcessDefinitionKeyContext.Provider>
  );
});

export {ListView};
