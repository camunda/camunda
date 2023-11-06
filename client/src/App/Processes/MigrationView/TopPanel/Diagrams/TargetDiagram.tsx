/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {Header} from './Header';
import {DiagramWrapper} from './styled';
import {observer} from 'mobx-react';
import {TargetProcessField} from './TargetProcessField';
import {TargetVersionField} from './TargetVersionField';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {processXmlStore} from 'modules/stores/processXml/processXml.migration.target';

import {DiagramShell} from 'modules/components/DiagramShell';
import {Diagram} from 'modules/components/Diagram';
import {useEffect} from 'react';

const TargetDiagram: React.FC = observer(() => {
  const {
    migrationState: {selectedTargetProcess, selectedTargetVersion},
    selectedTargetProcessId,
  } = processesStore;
  const isDiagramLoading = processXmlStore.state.status === 'fetching';

  const isVersionSelected = selectedTargetVersion !== null;

  useEffect(() => {
    if (selectedTargetProcessId !== undefined) {
      processXmlStore.fetchProcessXml(selectedTargetProcessId);
    }
  }, [selectedTargetProcessId]);

  const getStatus = () => {
    if (isDiagramLoading) {
      return 'loading';
    }
    if (processXmlStore.state.status === 'error') {
      return 'error';
    }
    if (!isVersionSelected) {
      return 'empty';
    }
    return 'content';
  };

  return (
    <DiagramWrapper>
      <Header
        mode={processInstanceMigrationStore.isSummaryStep ? 'view' : 'edit'}
        label="Target"
        processName={selectedTargetProcess?.name ?? ''}
        processVersion={selectedTargetVersion?.toString() ?? ''}
      >
        <TargetProcessField />
        <TargetVersionField />
      </Header>
      <DiagramShell
        status={getStatus()}
        emptyMessage={{
          message: 'Select a target process and version',
        }}
        messagePosition="center"
      >
        {processXmlStore.state.xml !== null && (
          <Diagram
            xml={processXmlStore.state.xml}
            selectableFlowNodes={processXmlStore.selectableIds}
            // TODO https://github.com/camunda/operate/issues/5732
            selectedFlowNodeId={undefined}
            onFlowNodeSelection={() => {}}
            // overlaysData={[]}
          >
            {/* overlays here  */}
          </Diagram>
        )}
      </DiagramShell>
    </DiagramWrapper>
  );
});

export {TargetDiagram};
