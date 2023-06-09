/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {CarbonPopover, Form, Icon, Button, ColorPicker} from 'components';
import {t} from 'translation';
import {isCategoricalBar} from 'services';

import * as visualizations from './visualizations';
import ShowInstanceCount from './ShowInstanceCount';
import DateVariableUnit from './DateVariableUnit';
import BucketSize from './BucketSize';
import PrecisionConfig from './visualizations/PrecisionConfig';

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
    const {data} = this.props.report;

    this.updateConfiguration(
      convertToChangeset({
        precision: null,
        targetValue: {
          active: false,
          isKpi: false,
          countProgress: {
            baseline: '0',
            target: '100',
            isBelow: false,
          },
          durationProgress: {
            baseline: {
              value: '0',
              unit: 'hours',
            },
            target: {
              value: '2',
              unit: 'hours',
              isBelow: false,
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
          columnOrder: data.configuration.tableColumns.columnOrder,
        },
        pointMarkers: true,
        xLabel: '',
        yLabel: '',
        color: ColorPicker.dark.steelBlue,
        groupByDateVariableUnit: 'automatic',
        distributeByDateVariableUnit: 'automatic',
        customBucket: {
          active: false,
          bucketSize: '10.0',
          bucketSizeUnit: 'minute',
          baseline: '0.0',
          baselineUnit: 'minute',
        },
        distributeByCustomBucket: {
          active: false,
          bucketSize: '10.0',
          bucketSizeUnit: 'minute',
          baseline: '0.0',
          baselineUnit: 'minute',
        },
        measureVisualizations: {
          frequency: 'bar',
          duration: 'line',
        },
        stackedBar: false,
        horizontalBar: isCategoricalBar(data),
        logScale: false,
      }),
      true
    );
  };

  updateConfiguration = (change, needsReevaluation) => {
    this.props.onChange({configuration: change}, needsReevaluation);
  };

  render() {
    const {report, type, disabled, autoPreviewDisabled} = this.props;
    const {configuration, view} = report.data;
    const Component = visualizations[type];

    const isRawDataReport = !report.combined && report.data.view?.properties[0] === 'rawData';

    const isPercentageOnly =
      view?.properties.includes('percentage') && view.properties.length === 1;

    const enablePopover =
      Component && !disabled && (!Component.isDisabled || !Component.isDisabled(report));

    return (
      <div className="Configuration">
        <CarbonPopover
          tooltip={t('report.config.buttonTooltip')}
          title={<Icon type="settings" />}
          disabled={!enablePopover}
          className="configurationPopover"
        >
          <Form className="content" compact>
            {!report.combined && (
              <ShowInstanceCount
                configuration={configuration}
                onChange={this.updateConfiguration}
                label={report.reportType === 'decision' ? 'evaluation' : 'instance'}
              />
            )}
            <DateVariableUnit report={report} onChange={this.updateConfiguration} />
            <BucketSize
              disabled={autoPreviewDisabled}
              report={report}
              onChange={this.updateConfiguration}
            />
            {Component && <Component report={report} onChange={this.updateConfiguration} />}
            {(configuration.showInstanceCount || (!isPercentageOnly && !isRawDataReport)) && (
              <PrecisionConfig
                configuration={configuration}
                onChange={this.updateConfiguration}
                view={view}
                type={type}
              />
            )}
          </Form>
          <Button className="resetButton" onClick={this.resetToDefaults}>
            {t('report.config.reset')}
          </Button>
        </CarbonPopover>
      </div>
    );
  }
}
