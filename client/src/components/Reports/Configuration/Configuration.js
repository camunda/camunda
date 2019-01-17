import React from 'react';

import {Popover, Icon, Button, ColorPicker} from 'components';
import * as visualizations from './visualizations';
import ShowInstanceCount from './ShowInstanceCount';

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
    this.props.onChange({
      configuration: convertToChangeset({
        precision: null,
        targetValue: {
          active: false,
          countProgress: {
            baseline: 0,
            target: 100
          },
          durationProgress: {
            baseline: {
              value: 0,
              unit: 'hours'
            },
            target: {
              value: 2,
              unit: 'hours'
            }
          },
          countChart: {
            value: 100,
            isBelow: false
          },
          durationChart: {
            value: 2,
            unit: 'hours',
            isBelow: false
          }
        },
        hideRelativeValue: false,
        hideAbsoluteValue: false,
        alwaysShowAbsolute: false,
        alwaysShowRelative: false,
        showInstanceCount: false,
        excludedColumns: [],
        pointMarkers: true,
        xLabel: '',
        yLabel: '',
        color: [ColorPicker.dark.steelBlue]
      })
    });
  };

  updateConfiguration = (prop, value) => {
    this.props.onChange({
      configuration: {
        [prop]: {$set: value}
      }
    });
  };

  //TODO: remove me as soon as the backend correctly initializes the configuration field on report creation
  componentDidMount() {
    if (!this.props.configuration.pointMarkers) {
      this.resetToDefaults();
    }
  }

  render() {
    const {report, type, configuration} = this.props;
    const Component = visualizations[type];

    const disabledComponent = Component && Component.isDisabled && Component.isDisabled(report);

    return (
      <li className="Configuration">
        <Popover
          tooltip="Configuration Options"
          title={<Icon type="settings" />}
          disabled={!type || disabledComponent}
        >
          <div className="content">
            {!report.combined && (
              <ShowInstanceCount
                configuration={configuration}
                onChange={this.updateConfiguration}
              />
            )}
            {Component && (
              <Component
                configuration={configuration}
                report={report}
                onChange={this.updateConfiguration}
              />
            )}
            <Button className="resetButton" onClick={this.resetToDefaults}>
              Reset to Defaults
            </Button>
          </div>
        </Popover>
      </li>
    );
  }
}
