/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getColorFor} from '../colorsUtils';
import createDefaultChartOptions from '../defaultChart/createDefaultChartOptions';
import createHyperChartOptions from '../hyperChart/createHyperChartOptions';

export default function createTargetLineOptions(props) {
  if (props.report.hyper) {
    return createHyperChartOptions(props);
  } else {
    return createDefaultChartOptions(props);
  }
}

export function getTargetLineOptions(color, isBelowTarget, isHyper, isDark) {
  return {
    targetOptions: {
      borderColor: isHyper ? color : getColorFor('targetBar', isDark),
      pointBorderColor: getColorFor('targetBar', isDark),
      backgroundColor: 'transparent',
      legendColor: color,
      borderWidth: 2,
      renderArea: isBelowTarget ? 'bottom' : 'top',
      isTarget: true,
      datalabels: {
        display: false,
      },
    },
    normalLineOptions: {
      borderColor: color,
      backgroundColor: 'transparent',
      legendColor: color,
      borderWidth: 2,
      renderArea: isBelowTarget ? 'top' : 'bottom',
      isTarget: false,
    },
  };
}
