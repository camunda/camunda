import React, {Children, cloneElement} from 'react';

import {PANE_ID, EXPAND_STATE} from 'modules/constants';

import Pane from './Pane';
import {twoNodesPropType} from './service';
import * as Styled from './styled';

const paneIds = [PANE_ID.TOP, PANE_ID.BOTTOM];

export default class SplitPane extends React.Component {
  static propTypes = {
    children: twoNodesPropType
  };

  state = {
    expandedPaneId: null
  };

  getPaneExpandedState = paneId => {
    const {expandedPaneId} = this.state;

    if (expandedPaneId === null) {
      return EXPAND_STATE.DEFAULT;
    }

    if (expandedPaneId === paneId) {
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
        handleExpand: this.handleExpand
      });
    });
  };

  handleExpand = paneId => {
    const expandedPaneId = this.state.expandedPaneId === null ? paneId : null;

    this.setState({expandedPaneId});
  };

  render() {
    const children = this.getChildren();
    return <Styled.SplitPane {...this.props}>{children}</Styled.SplitPane>;
  }
}

SplitPane.Pane = Pane;
