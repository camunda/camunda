import React from 'react';
import PropTypes from 'prop-types';

import {OPERATION_TYPE} from 'modules/constants';
import {applyOperation} from 'modules/api/instances';

import ActionItems from './ActionItems';

import {wrapIdinQuery, isWithIncident, isRunning} from './service';

export default class Actions extends React.Component {
  static propTypes = {
    instance: PropTypes.object.isRequired
  };

  renderItem = operationType => {
    return (
      <ActionItems.Item
        type={operationType}
        onClick={() =>
          applyOperation(
            operationType,
            wrapIdinQuery(operationType, this.props.instance)
          )
        }
      />
    );
  };

  render() {
    return (
      <ActionItems>
        {isWithIncident(this.props.instance) &&
          this.renderItem(OPERATION_TYPE.UPDATE_RETRIES)}
        {isRunning(this.props.instance) &&
          this.renderItem(OPERATION_TYPE.CANCEL)}
      </ActionItems>
    );
  }
}
