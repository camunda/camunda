/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Button, Icon} from 'components';
import {t} from 'translation';

import {DurationHeatmapModal} from './DurationHeatmap';

import './TargetValueComparison.scss';

export default class TargetValueComparison extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      modalOpen: false,
    };
  }

  getConfig = () => this.props.report.data.configuration.heatmapTargetValue;

  hasValues = () => {
    const {values} = this.getConfig();
    return values && Object.keys(values).length > 0;
  };

  toggleMode = () => {
    if (this.getConfig().active) {
      this.setActive(false);
    } else if (!this.hasValues()) {
      this.openModal();
    } else {
      this.setActive(true);
    }
  };

  setActive = (active) => {
    this.props.onChange({
      configuration: {
        heatmapTargetValue: {
          active: {$set: active},
        },
      },
    });
  };

  openModal = async () => {
    this.setState({
      modalOpen: true,
    });
  };

  closeModal = () => {
    this.setState({
      modalOpen: false,
    });
  };

  confirmModal = (values) => {
    this.props.onChange({
      configuration: {
        heatmapTargetValue: {
          $set: {
            active: Object.keys(values).length > 0,
            values,
          },
        },
      },
    });
    this.closeModal();
  };

  isResultAvailable = () => typeof this.props.report.result !== 'undefined';

  render() {
    const {active} = this.getConfig();

    return (
      <div className="TargetValueComparison">
        <Button className="toggleButton" active={active} onClick={this.toggleMode}>
          {this.hasValues() ? t('report.config.goal.target') : t('common.add')}
          <Icon type={active ? 'show' : 'hide'} />
        </Button>
        <Button className="editButton" onClick={this.openModal}>
          <Icon type="edit" />
        </Button>
        {this.isResultAvailable() && (
          <DurationHeatmapModal
            open={this.state.modalOpen}
            onClose={this.closeModal}
            onConfirm={this.confirmModal}
            report={this.props.report}
          />
        )}
      </div>
    );
  }
}
