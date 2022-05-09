/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Button, Icon} from 'components';
import {t} from 'translation';

import ReportModal from './ReportModal';

const size = {width: 6, height: 4};

export default class AddButton extends React.Component {
  state = {open: false};

  openModal = () => {
    this.setState({
      open: true,
    });
  };

  closeModal = (evt) => {
    if (evt) {
      evt.stopPropagation();
    }
    this.setState({
      open: false,
    });
  };

  addReport = (props) => {
    this.closeModal();

    // position does not matter because the report will be positioned by the user
    const payload = {
      configuration: null,
      position: {x: 0, y: 0},
      dimensions: size,
      ...props,
    };

    this.props.addReport(payload);
  };

  render() {
    return (
      <Button main className="AddButton tool-button" onClick={this.openModal}>
        <Icon type="plus" /> {t('dashboard.addButton.addReport')}
        {this.state.open && <ReportModal close={this.closeModal} confirm={this.addReport} />}
      </Button>
    );
  }
}
