/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processesStore} from 'modules/stores/processes/processes.migration';
import {processXmlStore} from 'modules/stores/processXml/processXml.migration.source';
import {Header} from './Header';
import {DiagramWrapper} from './styled';
import {observer} from 'mobx-react';
import {DiagramShell} from 'modules/components/DiagramShell';
import {Diagram} from 'modules/components/Diagram';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';

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
      <DiagramShell status="content">
        {processXmlStore.state.xml !== null && (
          <Diagram
            xml={processXmlStore.state.xml}
            selectableFlowNodes={processXmlStore.selectableIds}
            selectedFlowNodeId={
              processInstanceMigrationStore.state.selectedSourceFlowNodeId
            }
            onFlowNodeSelection={(flowNodeId) => {
              processInstanceMigrationStore.selectSourceFlowNode(flowNodeId);
            }}
            // overlaysData={[]}
          >
            {/* overlays here  */}
          </Diagram>
        )}
      </DiagramShell>
    </DiagramWrapper>
  );
});

export {SourceDiagram};
