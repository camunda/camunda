/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {DurationHeatmapModal} from './DurationHeatmap';

import {Button, Icon} from 'components';

import './TargetValueComparison.scss';
import {t} from 'translation';

export default class TargetValueComparison extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      modalOpen: false
    };
  }

  getConfig = () => this.props.report.data.configuration.heatmapTargetValue;

  toggleMode = () => {
    const {active, values} = this.getConfig();

    if (active) {
      this.setActive(false);
    } else if (!values || Object.keys(values).length === 0) {
      this.openModal();
    } else {
      this.setActive(true);
    }
  };

  setActive = active => {
    this.props.onChange({
      configuration: {
        heatmapTargetValue: {
          active: {$set: active}
        }
      }
    });
  };

  openModal = async () => {
    this.setState({
      modalOpen: true
    });
  };

  closeModal = () => {
    this.setState({
      modalOpen: false
    });
  };

  confirmModal = values => {
    this.props.onChange({
      configuration: {
        heatmapTargetValue: {
          $set: {
            active: Object.keys(values).length > 0,
            values
          }
        }
      }
    });
    this.closeModal();
  };

  render() {
    return (
      <div className="TargetValueComparison">
        <Button className="toggleButton" active={this.getConfig().active} onClick={this.toggleMode}>
          {t('report.heatTarget.label')}
        </Button>
        <Button className="editButton" onClick={this.openModal}>
          <Icon type="settings" />
        </Button>
        <DurationHeatmapModal
          open={this.state.modalOpen}
          onClose={this.closeModal}
          onConfirm={this.confirmModal}
          report={this.props.report}
        />
      </div>
    );
  }
}
