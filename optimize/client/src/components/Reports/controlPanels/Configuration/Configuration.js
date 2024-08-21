/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Component} from 'react';
import {Settings} from '@carbon/icons-react';
import {Stack, Form, Button} from '@carbon/react';

import {Popover, ColorPicker} from 'components';
import {t} from 'translation';
import {isCategoricalBar} from 'services';

import * as visualizations from './visualizations';
import ShowInstanceCount from './ShowInstanceCount';
import DateVariableUnit from './DateVariableUnit';
import BucketSize from './BucketSize';
import PrecisionConfig from './PrecisionConfig';

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

export default class Configuration extends Component {
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
          includeNewVariables: false,
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
    const {data, result} = report;
    const {configuration, view, distributedBy, groupBy} = data;
    const Component = visualizations[type];

    const isRawDataReport = view?.properties[0] === 'rawData';

    const isPercentageOnly =
      view?.properties.includes('percentage') && view.properties.length === 1;

    const enablePopover =
      Component && !disabled && (!Component.isDisabled || !Component.isDisabled(report));

    return (
      <div className="Configuration">
        <Popover
          isTabTip
          trigger={
            <Popover.Button
              size="md"
              kind="ghost"
              hasIconOnly
              renderIcon={Settings}
              iconDescription={t('report.config.buttonTooltip')}
              disabled={!enablePopover}
              tooltipPosition="left"
            />
          }
          className="configurationPopover"
          align="bottom-right"
          floating
        >
          <Form
            onSubmit={(e) => {
              // We do this to prevent any of the form inputs or enter key from triggering the form submission
              e.preventDefault();
            }}
            className="content"
          >
            <Stack gap={4}>
              <ShowInstanceCount
                showInstanceCount={configuration.showInstanceCount}
                onChange={this.updateConfiguration}
              />
              <DateVariableUnit
                configuration={configuration}
                groupBy={groupBy}
                distributedBy={distributedBy}
                onChange={this.updateConfiguration}
              />
              <BucketSize
                configuration={configuration}
                groupBy={groupBy}
                distributedBy={distributedBy}
                reportResult={result}
                disabled={autoPreviewDisabled}
                onChange={this.updateConfiguration}
              />
              {Component && (
                <Component
                  report={report}
                  onChange={this.updateConfiguration}
                  autoPreviewDisabled={autoPreviewDisabled}
                />
              )}
              {(configuration.showInstanceCount || (!isPercentageOnly && !isRawDataReport)) && (
                <PrecisionConfig
                  configuration={configuration}
                  onChange={this.updateConfiguration}
                  view={view}
                  type={type}
                />
              )}
            </Stack>
          </Form>
          <hr />
          <Button size="sm" kind="ghost" className="resetButton" onClick={this.resetToDefaults}>
            {t('report.config.reset')}
          </Button>
        </Popover>
      </div>
    );
  }
}
