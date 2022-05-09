/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classnames from 'classnames';
import {Button, Icon} from 'components';

import './Notification.scss';

export default class Notification extends React.Component {
  state = {};

  getIconType = () => {
    switch (this.props.config.type) {
      case 'success':
        return 'check-large';
      case 'warning':
        return 'warning';
      case 'error':
        return 'error';
      case 'hint':
        return 'hint';
      default:
        return null;
    }
  };

  render() {
    const iconType = this.getIconType();

    return (
      <div
        className={classnames('Notification', this.props.config.type, {
          closing: this.state.closing,
        })}
        onClick={this.keepOpen}
      >
        {iconType && <Icon type={iconType} />}
        {this.props.config.text}
        <Button className="close" onClick={this.close}>
          <Icon type="close-large" />
        </Button>
      </div>
    );
  }

  componentDidMount() {
    if (!this.props.config.stayOpen) {
      this.closeTrigger = setTimeout(this.close, this.props.config.duration || 4350);
    }
  }

  keepOpen = () => {
    clearTimeout(this.closeTrigger);
  };

  close = () => {
    this.setState({closing: true});
    setTimeout(this.props.remove, 350);
  };
}
