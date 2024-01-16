/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.migration.source';
import {processXmlStore} from 'modules/stores/processXml/processXml.migration.source';
import {processesStore} from 'modules/stores/processes/processes.list';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  useEffect(() => {
    return () => {
      processesStore.reset();
      processInstanceMigrationStore.reset();
      processXmlStore.reset();
      processStatisticsStore.reset();
    };
  });

  return (
    <>
      {children}
      <button
        onClick={() => processInstanceMigrationStore.setCurrentStep('summary')}
      >
        Summary
      </button>
      <button
        onClick={() =>
          processInstanceMigrationStore.setCurrentStep('elementMapping')
        }
      >
        Element Mapping
      </button>
      <button
        onClick={() => {
          processInstanceMigrationStore.updateFlowNodeMapping({
            sourceId: 'ServiceTask_0kt6c5i',
            targetId: 'ServiceTask_0kt6c5i',
          });
        }}
      >
        map elements
      </button>
    </>
  );
};

export {Wrapper};
