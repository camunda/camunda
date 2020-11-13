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
import DateVariableUnit from './DateVariableUnit';
import BucketSize from './BucketSize';
import {t} from 'translation';

import './Configuration.scss';

function convertToChangeset(config) {
  return Object.keys(config).reduce(
    (obj, curr) => ({
      ...obj,
      [curr]: {$set: config[curr]},
    }),
    {}
  );
}

export default class Configuration extends React.Component {
  resetToDefaults = () => {
    this.props.onChange(
      {
        distributedBy: {
          $set: {
            type: 'none',
            value: null,
          },
        },
        configuration: convertToChangeset({
          aggregationType: 'avg',
          userTaskDurationTime: 'total',
          flowNodeExecutionState: 'all',
          precision: null,
          targetValue: {
            active: false,
            countProgress: {
              baseline: '0',
              target: '100',
            },
            durationProgress: {
              baseline: {
                value: '0',
                unit: 'hours',
              },
              target: {
                value: '2',
                unit: 'hours',
              },
            },
            countChart: {
              value: '100',
              isBelow: false,
            },
            durationChart: {
              value: '2',
              unit: 'hours',
              isBelow: false,
            },
          },
          hideRelativeValue: false,
          hideAbsoluteValue: false,
          alwaysShowAbsolute: false,
          alwaysShowRelative: false,
          showInstanceCount: false,
          showGradientBars: true,
          tableColumns: {
            includeNewVariables: true,
            includedColumns: [],
            excludedColumns: [],
          },
          pointMarkers: true,
          xLabel: '',
          yLabel: '',
          color: ColorPicker.dark.steelBlue,
          hiddenNodes: {
            active: false,
            keys: [],
          },
          groupByDateVariableUnit: 'automatic',
          distributeByDateVariableUnit: 'automatic',
          customBucket: {
            active: false,
            bucketSize: '10',
            bucketSizeUnit: 'minute',
            baseline: '0',
            baselineUnit: 'minute',
          },
          distributeByCustomBucket: {
            active: false,
            bucketSize: '10',
            bucketSizeUnit: 'minute',
            baseline: '0',
            baselineUnit: 'minute',
          },
        }),
      },
      true
    );
  };

  updateConfiguration = (change, needsReevaluation) => {
    this.props.onChange({configuration: change}, needsReevaluation);
  };

  isResultAvailable = ({result, combined}) => {
    return result && combined ? Object.keys(result.data).length > 0 : result;
  };

  render() {
    const {report, type, onChange} = this.props;
    const Component = visualizations[type];

    const enablePopover =
      Component &&
      this.isResultAvailable(report) &&
      (!Component.isDisabled || !Component.isDisabled(report));

    return (
      <div className="Configuration">
        <Popover
          tooltip={t('report.config.buttonTooltip')}
          title={<Icon type="settings" />}
          disabled={!enablePopover}
        >
          <Form className="content" compact>
            {!report.combined && (
              <ShowInstanceCount
                configuration={report.data.configuration}
                onChange={this.updateConfiguration}
                label={report.reportType === 'decision' ? 'evaluation' : 'instance'}
              />
            )}
            <DateVariableUnit report={report} onChange={this.updateConfiguration} />
            <BucketSize report={report} onChange={this.updateConfiguration} />
            <AggregationType report={report} onChange={this.updateConfiguration} />
            <UserTaskDurationTime report={report} onChange={this.updateConfiguration} />
            {Component && <Component report={report} onChange={this.updateConfiguration} />}
            <DistributedBy report={report} onChange={onChange} />
            <NodeStatus report={report} onChange={this.updateConfiguration} />
            <VisibleNodesFilter report={report} onChange={this.updateConfiguration} />
          </Form>
          <Button className="resetButton" onClick={this.resetToDefaults}>
            {t('report.config.reset')}
          </Button>
        </Popover>
      </div>
    );
  }
}
