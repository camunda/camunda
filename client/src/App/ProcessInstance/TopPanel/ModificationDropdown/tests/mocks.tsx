/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render} from 'modules/testing-library';
import {PROCESS_INSTANCE_ID} from 'modules/mocks/metadata';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {createInstance} from 'modules/testUtils';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {createRef, useEffect} from 'react';
import {MemoryRouter} from 'react-router-dom';
import {ModificationDropdown} from '..';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {modificationsStore} from 'modules/stores/modifications';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    initializeStores();

    return resetStores;
  }, []);

  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/processes/1']}>{children}</MemoryRouter>
    </ThemeProvider>
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
    }
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
    })
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
