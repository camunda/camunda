/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Children, cloneElement} from 'react';

import {PANE_ID, EXPAND_STATE} from 'modules/constants';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

import Pane from './Pane';
import * as Styled from './styled';

const paneIds = [PANE_ID.TOP, PANE_ID.BOTTOM];

type Props = {
  expandedPaneId?: string;
  titles?: {
    top?: string;
    bottom?: string;
  };
};

type State = any;

class SplitPane extends React.Component<Props, State> {
  static Pane = Pane;

  state = {
    // @ts-expect-error ts-migrate(2464) FIXME: A computed property name must be of type 'string',... Remove this comment to see the full error message
    [this.props.expandedPaneId]: null,
  };

  getPaneExpandedState = (paneId: any) => {
    const {expandedPaneId} = this.props;

    const panelStates = getStateLocally('panelStates');

    // @ts-expect-error ts-migrate(2538) FIXME: Type 'undefined' cannot be used as an index type.
    if (!panelStates[expandedPaneId]) {
      return EXPAND_STATE.DEFAULT;
    }

    // @ts-expect-error ts-migrate(2538) FIXME: Type 'undefined' cannot be used as an index type.
    if (panelStates[expandedPaneId] === paneId) {
      return EXPAND_STATE.EXPANDED;
    }

    return EXPAND_STATE.COLLAPSED;
  };

  getChildren = () => {
    return Children.map(this.props.children, (child, idx) => {
      const paneId = paneIds[idx];
      let expandState = this.getPaneExpandedState(paneId);
      // @ts-expect-error ts-migrate(2769) FIXME: Type 'undefined' is not assignable to type 'ReactE... Remove this comment to see the full error message
      return cloneElement(child, {
        paneId,
        expandState,
        handleExpand: this.handleExpand,
        titles: this.props.titles,
      });
    });
  };

  handleExpand = (paneId: any) => {
    const {expandedPaneId} = this.props;
    const panelStates = getStateLocally('panelStates');

    const expandState = {
      // @ts-expect-error ts-migrate(2464) FIXME: A computed property name must be of type 'string',... Remove this comment to see the full error message
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

export default SplitPane;
