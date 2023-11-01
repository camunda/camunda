/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {MigrationView} from './MigrationView';
import {ListView} from './ListView';

const Processes: React.FC = observer(() => {
  return processInstanceMigrationStore.isEnabled ? (
    <MigrationView />
  ) : (
    <ListView />
  );
});

export {Processes};
