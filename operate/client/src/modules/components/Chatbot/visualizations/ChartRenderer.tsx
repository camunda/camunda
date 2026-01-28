/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {LineChart} from '@carbon/charts-react';
import '@carbon/charts-react/styles.css';
import type {VisualizationData} from './types';
import {ChartContainer} from './styled';

type ChartRendererProps = {
  visualization: VisualizationData;
};

const ChartRenderer: React.FC<ChartRendererProps> = ({visualization}) => {
  if (visualization.type === 'none' || !visualization.data || visualization.data.length === 0) {
    return null;
  }

  if (visualization.type === 'timeline') {
    return (
      <ChartContainer>
        {visualization.title && <h4>{visualization.title}</h4>}
        <LineChart
          data={visualization.data}
          options={{
            theme: 'g100',
            ...visualization.options,
          }}
        />
      </ChartContainer>
    );
  }

  return null;
};

export {ChartRenderer};
