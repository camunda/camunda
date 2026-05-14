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
import {BpmnWidget} from './widgets/BpmnWidget';
import {ChartWidget} from './widgets/ChartWidget';
import {TextWidget} from './widgets/TextWidget';
import {KpiWidget} from './widgets/KpiWidget';
import {StatusGridWidget} from './widgets/StatusGridWidget';
import {ActivityFeedWidget} from './widgets/ActivityFeedWidget';
import {FunnelWidget} from './widgets/FunnelWidget';
import {TrendWidget} from './widgets/TrendWidget';
import {WidgetFrame} from './WidgetFrame';
import type {WidgetConfig} from './types';

type Props = {
  config: WidgetConfig;
  onRemove?: () => void;
  onUpdate?: (next: WidgetConfig) => void;
};

function renderInner(config: WidgetConfig): React.ReactElement {
  if (config.type === 'metric') {
    return <MetricWidget config={config} />;
  }
  if (config.type === 'table') {
    return <TableWidget config={config} />;
  }
  if (config.type === 'bpmn') {
    return <BpmnWidget config={config} />;
  }
  if (config.type === 'chart') {
    return <ChartWidget config={config} />;
  }
  if (config.type === 'text') {
    return <TextWidget config={config} />;
  }
  if (config.type === 'kpi') {
    return <KpiWidget config={config} />;
  }
  if (config.type === 'status-grid') {
    return <StatusGridWidget config={config} />;
  }
  if (config.type === 'activity-feed') {
    return <ActivityFeedWidget config={config} />;
  }
  if (config.type === 'funnel') {
    return <FunnelWidget config={config} />;
  }
  if (config.type === 'trend') {
    return <TrendWidget config={config} />;
  }
  return (
    <div>
      <p>Unsupported widget type: {(config as {type: string}).type}</p>
    </div>
  );
}

const WidgetRenderer: React.FC<Props> = ({config, onRemove, onUpdate}) => {
  // Text widgets are pure narrative — no need for the frame's "show details"
  // button (the markdown IS the content). They still get the remove button
  // via a slim wrapper inside the slot's grid placement.
  if (config.type === 'text') {
    return (
      <WidgetFrame config={config} onRemove={onRemove} hideDetails>
        {renderInner(config)}
      </WidgetFrame>
    );
  }
  return (
    <WidgetFrame config={config} onRemove={onRemove} onUpdate={onUpdate}>
      {renderInner(config)}
    </WidgetFrame>
  );
};

export {WidgetRenderer};
