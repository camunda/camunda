/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import BarChartConfig from './BarChartConfig';
import PointMarkersConfig from './subComponents/PointMarkersConfig';

export default function LineChartConfig({onChange, report}) {
  return (
    <>
      <PointMarkersConfig {...{onChange, configuration: report.data.configuration}} />
      <BarChartConfig {...{onChange, report}} />
    </>
  );
}
