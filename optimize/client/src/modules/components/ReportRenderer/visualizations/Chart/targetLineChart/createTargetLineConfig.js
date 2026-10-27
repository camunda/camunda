/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
    plugins: createPlugins(props),
  };
}
