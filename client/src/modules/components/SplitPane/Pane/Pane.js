import React, {Children, cloneElement} from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import {PANE_ID, EXPAND_STATE, DIRECTION} from 'modules/constants';

import * as Styled from './styled';

export default class Pane extends React.Component {
  static propTypes = {
    handleExpand: PropTypes.func,
    paneId: PropTypes.oneOf(Object.values(PANE_ID)),
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  handleTopExpand = () => {
    this.props.handleExpand(PANE_ID.TOP);
  };

  handleBottomExpand = () => {
    this.props.handleExpand(PANE_ID.BOTTOM);
  };

  getChildren = () => {
    const {expandState} = this.props;

    const children = Children.map(this.props.children, child =>
      cloneElement(child, {expandState})
    );

    return children;
  };

  getBottomPaneButtons = () => {
    const {expandState} = this.props;

    const isTopButtonVisible = expandState !== EXPAND_STATE.COLLAPSED;
    const isBottomButtonVisible = expandState !== EXPAND_STATE.EXPANDED;

    return (
      <Styled.ButtonsContainer>
        {isTopButtonVisible && (
          <Styled.PaneExpandButton
            onClick={this.handleTopExpand}
            direction={DIRECTION.DOWN}
          />
        )}
        {isBottomButtonVisible && (
          <Styled.PaneExpandButton
            onClick={this.handleBottomExpand}
            direction={DIRECTION.UP}
          />
        )}
      </Styled.ButtonsContainer>
    );
  };

  render() {
    return (
      <Styled.Pane {...this.props} expandState={this.props.expandState}>
        {this.getChildren()}
        {this.props.paneId === PANE_ID.BOTTOM && this.getBottomPaneButtons()}
      </Styled.Pane>
    );
  }
}

Pane.Header = Panel.Header;
Pane.Body = Styled.Body;
Pane.Footer = Styled.Footer;
