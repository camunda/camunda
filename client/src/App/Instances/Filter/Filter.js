import React from 'react';

import Checkbox from 'modules/components/Checkbox';

import * as Styled from './styled.js';

export default class Filter extends React.Component {
  handleRunningChange = () => {
    const {withIncidents, withoutIncidents, running} = this.props.filter;
    if (running && !withIncidents && !withoutIncidents) {
      this.props.onChange({
        running: {$set: false}
      });
    } else {
      this.props.onChange({
        withIncidents: {$set: false},
        withoutIncidents: {$set: false},
        running: {$set: true}
      });
    }
  };

  handleActiveChange = () => {
    const {withIncidents, withoutIncidents, running} = this.props.filter;
    if (running) {
      if (withIncidents) {
        this.props.onChange({
          withIncidents: {$set: false}
        });
      } else {
        if (withoutIncidents) {
          this.props.onChange({
            withoutIncidents: {$set: false},
            running: {$set: false}
          });
        } else {
          this.props.onChange({
            withIncidents: {$set: true}
          });
        }
      }
    } else {
      this.props.onChange({
        running: {$set: true},
        withoutIncidents: {$set: true}
      });
    }
  };

  handleIncidentChange = () => {
    const {withoutIncidents, withIncidents, running} = this.props.filter;
    if (running) {
      if (withoutIncidents) {
        this.props.onChange({
          withoutIncidents: {$set: false}
        });
      } else {
        if (withIncidents) {
          this.props.onChange({
            withIncidents: {$set: false},
            running: {$set: false}
          });
        } else {
          this.props.onChange({
            withoutIncidents: {$set: true}
          });
        }
      }
    } else {
      this.props.onChange({
        running: {$set: true},
        withIncidents: {$set: true}
      });
    }
  };

  isIndeterminate = () => {
    const {withIncidents, withoutIncidents} = this.props.filter;
    return !!(withIncidents || withoutIncidents);
  };

  render() {
    const {withIncidents, withoutIncidents, running} = this.props.filter;

    return (
      <Styled.Filters>
        <div>
          <Checkbox
            label="Running Instances"
            isIndeterminate={this.isIndeterminate()}
            isChecked={running || false}
            onChange={this.handleRunningChange}
          />
        </div>
        <Styled.NestedFilters>
          <div>
            <Checkbox
              label="Active"
              isChecked={
                !!(withoutIncidents || (running && !this.isIndeterminate()))
              }
              onChange={this.handleActiveChange}
            />
          </div>
          <div>
            <Checkbox
              label="Incident"
              isChecked={
                !!(withIncidents || (running && !this.isIndeterminate()))
              }
              onChange={this.handleIncidentChange}
            />
          </div>
        </Styled.NestedFilters>
      </Styled.Filters>
    );
  }
}
