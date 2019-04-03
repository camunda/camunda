/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import createTargetLineData from './createTargetLineData';
import createTargetLineOptions from './createTargetLineOptions';
import createPlugins from '../createPlugins';
import './addChartType';

export default function createTargetLineConfig(props) {
  return {
    type: 'targetLine',
    data: createTargetLineData(props),
    options: createTargetLineOptions(props),
    plugins: createPlugins(props)
  };
}
