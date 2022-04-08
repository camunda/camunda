/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getColorFor} from '../colorsUtils';
import createDefaultChartOptions from '../defaultChart/createDefaultChartOptions';
import createCombinedChartOptions from '../combinedChart/createCombinedChartOptions';

export default function createTargetLineOptions(props) {
  if (props.report.combined) {
    return createCombinedChartOptions(props);
  } else {
    return createDefaultChartOptions(props);
  }
}

export function getTargetLineOptions(color, isBelowTarget, isCombined, isDark) {
  return {
    targetOptions: {
      borderColor: isCombined ? color : getColorFor('targetBar', isDark),
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
