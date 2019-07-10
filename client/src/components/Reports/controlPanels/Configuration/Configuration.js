/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Popover, Form, Icon, Button, ColorPicker} from 'components';
import * as visualizations from './visualizations';
import ShowInstanceCount from './ShowInstanceCount';
import UserTaskDurationTime from './UserTaskDurationTime';
import AggregationType from './AggregationType';
import VisibleNodesFilter from './VisibleNodesFilter';
import NodeStatus from './NodeStatus';
import DistributedBy from './DistributedBy';

import './Configuration.scss';

function convertToChangeset(config) {
  return Object.keys(config).reduce(
    (obj, curr) => ({
      ...obj,
      [curr]: {$set: config[curr]}
    }),
    {}
  );
}

export default class Configuration extends React.Component {
  resetToDefaults = () => {
    this.updateConfiguration(
      convertToChangeset({
        aggregationType: 'avg',
        userTaskDurationTime: 'total',
        flowNodeExecutionState: 'all',
        distributedBy: 'none',
        precision: null,
        targetValue: {
          active: false,
          countProgress: {
            baseline: '0',
            target: '100'
          },
          durationProgress: {
            baseline: {
              value: '0',
              unit: 'hours'
            },
            target: {
              value: '2',
              unit: 'hours'
            }
          },
          countChart: {
            value: '100',
            isBelow: false
          },
          durationChart: {
            value: '2',
            unit: 'hours',
            isBelow: false
          }
        },
        hideRelativeValue: false,
        hideAbsoluteValue: false,
        alwaysShowAbsolute: false,
        alwaysShowRelative: false,
        showInstanceCount: false,
        showGradientBars: true,
        excludedColumns: [],
        pointMarkers: true,
        xLabel: '',
        yLabel: '',
        color: ColorPicker.dark.steelBlue,
        hiddenNodes: []
      }),
      true
    );
  };

  updateConfiguration = (change, needsReevaluation) => {
    this.props.onChange({configuration: change}, needsReevaluation);
  };

  render() {
    const {report, type, onChange} = this.props;
    const Component = visualizations[type];

    const disabledComponent = Component && Component.isDisabled && Component.isDisabled(report);

    return (
      <li className="Configuration">
        <Popover
          tooltip="Configuration Options"
          title={<Icon type="settings" />}
          disabled={!type || disabledComponent}
        >
          <Form className="content" compact>
            {!report.combined && (
              <ShowInstanceCount
                configuration={report.data.configuration}
                onChange={this.updateConfiguration}
                label={report.reportType === 'decision' ? 'Evaluation' : 'Instance'}
              />
            )}
            <AggregationType report={report} onChange={this.updateConfiguration} />
            <UserTaskDurationTime report={report} onChange={this.updateConfiguration} />
            {Component && <Component report={report} onChange={this.updateConfiguration} />}
            <DistributedBy report={report} onChange={onChange} />
            <NodeStatus report={report} onChange={this.updateConfiguration} />
            <VisibleNodesFilter report={report} onChange={this.updateConfiguration} />
          </Form>
          <Button className="resetButton" onClick={this.resetToDefaults}>
            Reset to Defaults
          </Button>
        </Popover>
      </li>
    );
  }
}
