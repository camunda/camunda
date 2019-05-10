/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Button} from 'components';
import NodeSelectionModal from './NodeSelectionModal';
import './VisibleNodesFilter.scss';

export default class NodeFilter extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      open: false
    };
  }

  close = evt => {
    this.setState({open: false});
  };

  render() {
    const {combined, data} = this.props.report;
    if (!combined && data.groupBy.type === 'flowNodes') {
      return (
        <div className="VisibleNodesFilter">
          <fieldset className="NodeFilter">
            <legend>Selected nodes to display</legend>
            <Button onClick={() => this.setState({open: true})}>Show Flow Nodes...</Button>
          </fieldset>
          {this.state.open && <NodeSelectionModal {...this.props} onClose={this.close} />}
        </div>
      );
    }
    return null;
  }
}
