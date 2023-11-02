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
import {TopPanel} from './TopPanel';
import {BottomPanel} from './BottomPanel';
import {Footer} from './Footer';

const MigrationView: React.FC = () => {
  useEffect(() => {
    return processInstanceMigrationStore.reset;
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
