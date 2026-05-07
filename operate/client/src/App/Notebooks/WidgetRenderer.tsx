/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {MetricWidget} from './widgets/MetricWidget';
import {TableWidget} from './widgets/TableWidget';
import type {WidgetConfig} from './types';

type Props = {
  config: WidgetConfig;
};

const WidgetRenderer: React.FC<Props> = ({config}) => {
  if (config.type === 'metric') {
    return <MetricWidget config={config} />;
  }

  if (config.type === 'table') {
    return <TableWidget config={config} />;
  }

  return (
    <div>
      <p>Unsupported widget type: {(config as {type: string}).type}</p>
    </div>
  );
};

export {WidgetRenderer};
