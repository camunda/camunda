/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
      labelText={label}
      hideLabel
    />
  );
}
