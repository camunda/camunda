/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Children, cloneElement} from 'react';
import PropTypes from 'prop-types';

import {PANE_ID, EXPAND_STATE} from 'modules/constants';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

import Pane from './Pane';
import {twoNodesPropType} from './service';
import * as Styled from './styled';

const paneIds = [PANE_ID.TOP, PANE_ID.BOTTOM];

class SplitPane extends React.Component {
  static propTypes = {
    children: twoNodesPropType,
    expandedPaneId: PropTypes.string,
    titles: PropTypes.shape({top: PropTypes.string, bottom: PropTypes.string}),
  };

  state = {
    [this.props.expandedPaneId]: null,
  };

  getPaneExpandedState = (paneId) => {
    const {expandedPaneId} = this.props;

    const panelStates = getStateLocally('panelStates');

    if (!panelStates[expandedPaneId]) {
      return EXPAND_STATE.DEFAULT;
    }

    if (panelStates[expandedPaneId] === paneId) {
      return EXPAND_STATE.EXPANDED;
    }

    return EXPAND_STATE.COLLAPSED;
  };

  getChildren = () => {
    return Children.map(this.props.children, (child, idx) => {
      const paneId = paneIds[idx];
      let expandState = this.getPaneExpandedState(paneId);
      return cloneElement(child, {
        paneId,
        expandState,
        handleExpand: this.handleExpand,
        titles: this.props.titles,
      });
    });
  };

  handleExpand = (paneId) => {
    const {expandedPaneId} = this.props;
    const panelStates = getStateLocally('panelStates');

    const expandState = {
      [expandedPaneId]: !panelStates[expandedPaneId] ? paneId : null,
    };

    storeStateLocally(expandState, 'panelStates');

    this.setState(expandState);
  };

  render() {
    const children = this.getChildren();

    return <Styled.SplitPane {...this.props}>{children}</Styled.SplitPane>;
  }
}

SplitPane.Pane = Pane;

export default SplitPane;
