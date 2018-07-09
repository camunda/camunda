import React, {Children, cloneElement} from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import {PANE_ID, EXPAND_STATE, DIRECTION} from 'modules/constants';

import * as Styled from './styled';

const paneExpandButton = {
  [PANE_ID.TOP]: {
    ExpandButton: Styled.TopExpandButton,
    directions: {
      EXPANDED: DIRECTION.UP,
      COLLAPSED: DIRECTION.DOWN,
      DEFAULT: DIRECTION.DOWN
    }
  },
  [PANE_ID.BOTTOM]: {
    ExpandButton: Styled.BottomExpandButton,
    directions: {
      EXPANDED: DIRECTION.DOWN,
      COLLAPSED: DIRECTION.UP,
      DEFAULT: DIRECTION.UP
    }
  }
};
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

  handleExpand = () => {
    this.props.handleExpand(this.props.paneId);
  };

  render() {
    const {expandState, paneId} = this.props;

    const children = Children.map(this.props.children, child =>
      cloneElement(child, {expandState})
    );

    const {
      ExpandButton,
      directions: {[expandState]: direction}
    } = paneExpandButton[paneId];

    return (
      <Styled.Pane {...this.props} expandState={expandState}>
        {children}
        <ExpandButton onClick={this.handleExpand} direction={direction} />
      </Styled.Pane>
    );
  }
}

Pane.Header = Panel.Header;
Pane.Body = Styled.Body;
Pane.Footer = Styled.Footer;
