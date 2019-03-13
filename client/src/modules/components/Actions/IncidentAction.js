/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {OPERATION_TYPE} from 'modules/constants';
import {applyOperation} from 'modules/api/instances';
import ActionStatus from 'modules/components/ActionStatus';

import ActionItems from './ActionItems';

import * as Styled from './styled';

export default class Actions extends React.Component {
  static propTypes = {
    incident: PropTypes.object.isRequired,
    onButtonClick: PropTypes.func,
    instanceId: PropTypes.string.isRequired,
    showSpinner: PropTypes.bool
  };

  state = {
    isSpinnerVisible: false
  };

  handleOnClick = async e => {
    e.stopPropagation();
    this.setState({isSpinnerVisible: true});
    await applyOperation(
      this.props.instanceId,
      OPERATION_TYPE.RESOLVE_INCIDENT,
      this.props.incident.id
    );

    this.props.onButtonClick && this.props.onButtonClick();
  };

  render() {
    const isSpinnerVisible =
      this.state.isSpinnerVisible || this.props.showSpinner;

    return (
      <Styled.Actions>
        {isSpinnerVisible && <ActionStatus.Spinner />}

        <ActionItems>
          <ActionItems.Item
            type={OPERATION_TYPE.RESOLVE_INCIDENT}
            onClick={this.handleOnClick}
            title="Retry Incident"
          />
        </ActionItems>
      </Styled.Actions>
    );
  }
}
