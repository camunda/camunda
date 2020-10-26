/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';

import {DEFAULT_FILTER_CONTROLLED_VALUES, PAGE_TITLE} from 'modules/constants';

import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';

import {DiagramPanel} from './DiagramPanel';
import {ListPanel} from './ListPanel';
import Filters from './Filters';
import OperationsPanel from './OperationsPanel';
import {filtersStore} from 'modules/stores/filters';
import {instancesStore} from 'modules/stores/instances';
import {workflowStatisticsStore} from 'modules/stores/workflowStatistics';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';

import {observer} from 'mobx-react';
import * as Styled from './styled.js';

const Instances = observer((props) => {
  useEffect(() => {
    filtersStore.init();
    instanceSelectionStore.init();
    instancesDiagramStore.init();
    workflowStatisticsStore.init();
    instancesStore.init();
    document.title = PAGE_TITLE.INSTANCES;
    return () => {
      filtersStore.reset();
      instanceSelectionStore.reset();
      instancesDiagramStore.reset();
      workflowStatisticsStore.reset();
      instancesStore.reset();
    };
  }, []);

  useEffect(() => {
    const {history, location} = props;
    filtersStore.setUrlParameters(history, location);
  }, [props]);

  return (
    <Styled.Instances>
      <VisuallyHiddenH1>Camunda Operate Instances</VisuallyHiddenH1>
      <Styled.Content>
        <Styled.FilterSection>
          <Filters
            filter={{
              ...DEFAULT_FILTER_CONTROLLED_VALUES,
              ...filtersStore.decodedFilters,
            }}
          />
        </Styled.FilterSection>
        <Styled.SplitPane
          titles={{top: 'Workflow', bottom: 'Instances'}}
          expandedPaneId="instancesExpandedPaneId"
        >
          <DiagramPanel />
          <ListPanel />
        </Styled.SplitPane>
      </Styled.Content>
      <OperationsPanel />
    </Styled.Instances>
  );
});

export {Instances};
