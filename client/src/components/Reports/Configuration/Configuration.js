import React from 'react';

import {Popover, Icon, Button} from 'components';
import * as visualizations from './visualizations';

import './Configuration.scss';

export default class Configuration extends React.Component {
  resetToDefaults = resetTargetValue => {
    const Component = visualizations[this.props.type];
    this.props.onChange({
      configuration: {
        ...this.props.configuration,
        ...(resetTargetValue ? {targetValue: null} : {}),
        ...(Component ? Component.defaults : {})
      }
    });
  };

  updateConfiguration = (prop, value) => {
    this.props.onChange({
      configuration: {
        ...this.props.configuration,
        [prop]: value
      }
    });
  };

  componentDidUpdate(prevProps) {
    const Component = visualizations[this.props.type];
    // reset visualization options to default when visualization changes
    if (prevProps.type !== this.props.type) {
      this.resetToDefaults(!isBarOrLine(prevProps.type, this.props.type));
    }

    if (Component && Component.onUpdate) {
      const updates = Component.onUpdate(prevProps, this.props);
      if (updates) {
        this.props.onChange({
          configuration: {
            ...this.props.configuration,
            ...updates
          }
        });
      }
    }
  }

  render() {
    const Component = visualizations[this.props.type];
    return (
      <li className="Configuration">
        <Popover title={<Icon type="settings" />} disabled={!this.props.type}>
          <div className="content">
            {Component && (
              <Component
                configuration={this.props.configuration}
                report={this.props.report}
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

function isBarOrLine(currentVis, nextVis) {
  const barOrLine = ['bar', 'line'];
  return barOrLine.includes(currentVis) && barOrLine.includes(nextVis);
}
