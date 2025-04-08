/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'modules/testing-library';
import {PROCESS_INSTANCE_ID} from 'modules/mocks/metadata';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {createInstance} from 'modules/testUtils';
import {createRef, useEffect} from 'react';
import {MemoryRouter} from 'react-router-dom';
import {ModificationDropdown} from '..';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {modificationsStore} from 'modules/stores/modifications';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {Paths} from 'modules/Routes';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    initializeStores();

    return resetStores;
  }, []);

  return (
    <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
      {children}
    </MemoryRouter>
  );
};

const renderPopover = () => {
  const {container} = render(<svg />);
  const ref = createRef<HTMLDivElement>();

  return render(
    <ModificationDropdown
      selectedFlowNodeRef={container.querySelector('svg') ?? undefined}
      diagramCanvasRef={ref}
    />,
    {
      wrapper: Wrapper,
    },
  );
};

const initializeStores = () => {
  flowNodeSelectionStore.init();
  processInstanceDetailsStatisticsStore.init('processId');
  processInstanceDetailsDiagramStore.init();
  processInstanceDetailsStore.setProcessInstance(
    createInstance({
      id: PROCESS_INSTANCE_ID,
      state: 'ACTIVE',
      processId: 'processId',
    }),
  );
};

const resetStores = () => {
  flowNodeSelectionStore.reset();
  processInstanceDetailsStore.reset();
  modificationsStore.reset();
  processInstanceDetailsStatisticsStore.reset();
  processInstanceDetailsDiagramStore.reset();
  flowNodeMetaDataStore.reset();
};

export {renderPopover};
