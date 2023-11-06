/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FieldContainer, Label} from './styled';
import {observer} from 'mobx-react';
import {isNil} from 'lodash';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {Dropdown} from '@carbon/react';

const TargetVersionField: React.FC = observer(() => {
  const {
    migrationState: {selectedTargetVersion},
    targetProcessVersions,
  } = processesStore;

  return (
    <FieldContainer>
      <Label>Version</Label>
      <Dropdown
        id="targetProcessVersion"
        label="-"
        titleText="Target Version"
        hideLabel
        type="inline"
        onChange={async ({selectedItem}) => {
          if (!isNil(selectedItem)) {
            processesStore.setSelectedTargetVersion(selectedItem);
          }
        }}
        disabled={
          selectedTargetVersion === null && targetProcessVersions.length === 0
        }
        items={targetProcessVersions}
        size="sm"
        selectedItem={selectedTargetVersion}
      />
    </FieldContainer>
  );
});

export {TargetVersionField};
