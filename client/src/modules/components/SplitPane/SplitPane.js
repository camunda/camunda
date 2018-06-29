import React, {Children, cloneElement} from 'react';

import Pane from './Pane';
import {PANE_ID, PANE_STATE} from './Pane/constants';
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
      return PANE_STATE.DEFAULT;
    }

    if (expandedPaneId === paneId) {
      return PANE_STATE.EXPANDED;
    }

    return PANE_STATE.COLLAPSED;
  };

  getChildren = () => {
    return Children.map(this.props.children, (child, idx) => {
      const paneId = paneIds[idx];
      let paneState = this.getPaneExpandedState(paneId);
      return cloneElement(child, {paneId, paneState, expand: this.expand});
    });
  };

  expand = paneId => {
    const expandedPaneId = this.state.expandedPaneId === null ? paneId : null;

    this.setState({expandedPaneId});
  };

  render() {
    const children = this.getChildren();
    return <Styled.SplitPane {...this.props}>{children}</Styled.SplitPane>;
  }
}

SplitPane.Pane = Pane;
