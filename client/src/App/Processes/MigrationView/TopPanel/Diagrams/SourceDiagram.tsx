/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processesStore} from 'modules/stores/processes/processes.migration';
import {Header} from './Header';
import {DiagramWrapper} from './styled';
import {observer} from 'mobx-react';

const SourceDiagram: React.FC = observer(() => {
  const {processName, version} = processesStore.getSelectedProcessDetails();

  return (
    <DiagramWrapper>
      <Header
        mode="view"
        label="Source"
        processName={processName}
        processVersion={version ?? ''}
      />
      Source Diagram
    </DiagramWrapper>
  );
});

export {SourceDiagram};
