/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render} from 'modules/testing-library';
import {PROCESS_INSTANCE_ID} from 'modules/mocks/metadata';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeStatesStore} from 'modules/stores/flowNodeStates';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {createInstance} from 'modules/testUtils';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {createRef} from 'react';
import {MemoryRouter} from 'react-router-dom';
import {ModificationDropdown} from '..';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
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
  flowNodeStatesStore.init('processId');
  processInstanceDetailsDiagramStore.init();
  processInstanceDetailsStore.setProcessInstance(
    createInstance({
      id: PROCESS_INSTANCE_ID,
      state: 'ACTIVE',
      processId: 'processId',
    })
  );
};

export {renderPopover, initializeStores};
