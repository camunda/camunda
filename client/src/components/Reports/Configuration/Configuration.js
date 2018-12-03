import React from 'react';

import {Popover, Icon, Button} from 'components';
import * as visualizations from './visualizations';

import './Configuration.scss';

export default class Configuration extends React.Component {
  resetToDefaults = () => {
    const Component = visualizations[this.props.type] || {};

    const defaults =
      typeof Component.defaults === 'function'
        ? Component.defaults(this.props)
        : Component.defaults || {};

    this.props.onChange({
      configuration: {
        ...this.props.configuration,
        ...defaults
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
    const report = this.props.report;
    const isEmptyCombined =
      report.combined && (!report.data.reportIds || !report.data.reportIds.length);

    return (
      <li className="Configuration">
        <Popover title={<Icon type="settings" />} disabled={!this.props.type || isEmptyCombined}>
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
