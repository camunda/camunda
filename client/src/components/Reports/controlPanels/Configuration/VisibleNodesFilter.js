/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Button} from 'components';
import NodeSelectionModal from './NodeSelectionModal';
import './VisibleNodesFilter.scss';

export default class VisibleNodesFilter extends React.Component {
  state = {
    open: false
  };

  close = evt => this.setState({open: false});
  open = evt => this.setState({open: true});

  render() {
    const {combined, data} = this.props.report;
    if (!combined && data.groupBy.type === 'flowNodes') {
      return (
        <div className="VisibleNodesFilter">
          <fieldset>
            <legend>Selected nodes to display</legend>
            <Button onClick={this.open}>Show Flow Nodes...</Button>
          </fieldset>
          {this.state.open && <NodeSelectionModal {...this.props} onClose={this.close} />}
        </div>
      );
    }
    return null;
  }
}
