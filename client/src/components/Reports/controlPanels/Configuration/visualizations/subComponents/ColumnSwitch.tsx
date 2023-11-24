/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Toggle} from '@carbon/react';

interface ColumnSwitchProps {
  switchId: string;
  label: string;
  excludedColumns: string[];
  includedColumns: string[];
  onChange: (change: {
    tableColumns: {
      excludedColumns: Record<string, string[]>;
      includedColumns: Record<string, string[]>;
    };
  }) => void;
}

export default function ColumnSwitch({
  switchId,
  label,
  excludedColumns,
  includedColumns,
  onChange,
}: ColumnSwitchProps) {
  return (
    <Toggle
      size="sm"
      id={switchId}
      key={switchId}
      className="ColumnSwitch"
      toggled={!excludedColumns.includes(switchId)}
      onToggle={(checked) => {
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
      labelA={label}
      labelB={label}
    />
  );
}
