/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';

import {DEFAULT_FILTER_CONTROLLED_VALUES, PAGE_TITLE} from 'modules/constants';

import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {InstancesPollProvider} from 'modules/contexts/InstancesPollContext';

import {DiagramPanel} from './DiagramPanel';
import ListPanel from './ListPanel';
import Filters from './Filters';
import OperationsPanel from './OperationsPanel';
import {filters} from 'modules/stores/filters';
import {instances} from 'modules/stores/instances';
import {workflowStatistics} from 'modules/stores/workflowStatistics';
import {instanceSelection} from 'modules/stores/instanceSelection';
import {instancesDiagram} from 'modules/stores/instancesDiagram';

import {observer} from 'mobx-react';
import * as Styled from './styled.js';

const Instances = observer((props) => {
  const {workflowInstances, isInitialLoadComplete, isLoading} = instances.state;
  useEffect(() => {
    filters.init();
    instanceSelection.init();
    instancesDiagram.init();
    workflowStatistics.init();
    instances.init();
    document.title = PAGE_TITLE.INSTANCES;
    return () => {
      filters.reset();
      instanceSelection.reset();
      instancesDiagram.reset();
      workflowStatistics.reset();
      instances.reset();
    };
  }, []);

  useEffect(() => {
    const {history, location} = props;
    filters.setUrlParameters(history, location);
  }, [props]);

  return (
    <InstancesPollProvider
      visibleIdsInListPanel={workflowInstances.map(({id}) => id)}
      filter={filters.state.filter}
    >
      <Styled.Instances>
        <VisuallyHiddenH1>Camunda Operate Instances</VisuallyHiddenH1>
        <Styled.Content>
          <Styled.FilterSection>
            <Filters
              filter={{
                ...DEFAULT_FILTER_CONTROLLED_VALUES,
                ...filters.decodedFilters,
              }}
            />
          </Styled.FilterSection>
          <Styled.SplitPane
            titles={{top: 'Workflow', bottom: 'Instances'}}
            expandedPaneId="instancesExpandedPaneId"
          >
            <DiagramPanel />
            <ListPanel
              instances={workflowInstances}
              isInitialLoadComplete={isInitialLoadComplete}
              isLoading={isLoading}
            />
          </Styled.SplitPane>
        </Styled.Content>
        <OperationsPanel />
      </Styled.Instances>
    </InstancesPollProvider>
  );
});

export {Instances};
