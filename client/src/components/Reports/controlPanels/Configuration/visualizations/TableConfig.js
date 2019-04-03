/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import ColumnSelection from './subComponents/ColumnSelection';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';
import GradientBarsSwitch from './subComponents/GradientBarsSwitch';

import {isDurationReport} from 'services';

export default function TableConfig({report, onChange}) {
  let typeSpecificComponent = null;

  const {property, operation} = (report.combined
    ? Object.values(report.result)[0]
    : report
  ).data.view;
  const viewType = property || operation;

  const groupBy = !report.combined && report.data.groupBy.type;

  switch (viewType) {
    case 'rawData':
      typeSpecificComponent = <ColumnSelection report={report} onChange={onChange} />;
      break;
    case 'frequency':
      typeSpecificComponent = (
        <>
          <RelativeAbsoluteSelection
            absolute={!report.data.configuration.hideAbsoluteValue}
            relative={!report.data.configuration.hideRelativeValue}
            onChange={(type, value) => {
              if (type === 'absolute') {
                onChange({hideAbsoluteValue: {$set: !value}});
              } else {
                onChange({hideRelativeValue: {$set: !value}});
              }
            }}
          />
          {groupBy === 'matchedRule' && (
            <GradientBarsSwitch configuration={report.data.configuration} onChange={onChange} />
          )}
        </>
      );
      break;
    default:
      typeSpecificComponent = null;
  }

  return typeSpecificComponent;
}

// disable popover for duration tables since they currently have no configuration
TableConfig.isDisabled = report => {
  return (
    report.combined &&
    report.data.reports &&
    report.data.reports.length &&
    isDurationReport(Object.values(report.result)[0])
  );
};
