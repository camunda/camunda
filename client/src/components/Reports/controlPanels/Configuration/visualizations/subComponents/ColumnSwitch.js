/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Switch} from 'components';

import './ColumnSwitch.scss';

export default function ColumnSwitch({
  switchId,
  label,
  excludedColumns,
  includedColumns,
  onChange,
}) {
  return (
    <Switch
      key={switchId}
      className="ColumnSwitch"
      checked={!excludedColumns.includes(switchId)}
      onChange={({target: {checked}}) => {
        if (checked) {
          onChange({
            tableColumns: {
              excludedColumns: {$set: excludedColumns.filter((entry) => switchId !== entry)},
              includedColumns: {$push: [switchId]},
            },
          });
        } else {
          onChange({
            tableColumns: {
              excludedColumns: {$push: [switchId]},
              includedColumns: {$set: includedColumns.filter((entry) => switchId !== entry)},
            },
          });
        }
      }}
      label={label}
    />
  );
}
