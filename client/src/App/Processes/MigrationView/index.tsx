/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {InstancesList} from 'App/Layout/InstancesList';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {TopPanel} from './TopPanel';
import {BottomPanel} from './BottomPanel';
import {Footer} from './Footer';
import {PAGE_TITLE} from 'modules/constants';
import {processXmlStore as processXmlMigrationSourceStore} from 'modules/stores/processXml/processXml.migration.source';
import {processXmlStore as processXmlMigrationTargetStore} from 'modules/stores/processXml/processXml.migration.target';

const MigrationView: React.FC = () => {
  useEffect(() => {
    document.title = PAGE_TITLE.INSTANCES;
  }, []);

  useEffect(() => {
    processesStore.fetchProcesses();

    return () => {
      processInstanceMigrationStore.reset();
      processesStore.reset();
      processXmlMigrationSourceStore.reset();
      processXmlMigrationTargetStore.reset();
    };
  }, []);

  return (
    <>
      <VisuallyHiddenH1>
        Operate Process Instances - Migration Mode
      </VisuallyHiddenH1>

      <InstancesList
        type="migrate"
        topPanel={<TopPanel />}
        bottomPanel={<BottomPanel />}
        footer={<Footer />}
      />
    </>
  );
};

export {MigrationView};
