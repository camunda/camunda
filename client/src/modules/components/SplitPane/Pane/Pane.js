import React, {Children, cloneElement} from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import {ICON_DIRECTION} from 'modules/constants/expandIcon';
import {PANE_ID, EXPAND_STATE} from 'modules/constants/splitPane';

import * as Styled from './styled';

const paneExpandButton = {
  [PANE_ID.TOP]: {
    ExpandButton: Styled.TopExpandButton,
    iconDirections: {
      EXPANDED: ICON_DIRECTION.UP,
      COLLAPSED: ICON_DIRECTION.DOWN,
      DEFAULT: ICON_DIRECTION.DOWN
    }
  },
  [PANE_ID.BOTTOM]: {
    ExpandButton: Styled.BottomExpandButton,
    iconDirections: {
      EXPANDED: ICON_DIRECTION.DOWN,
      COLLAPSED: ICON_DIRECTION.UP,
      DEFAULT: ICON_DIRECTION.UP
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
      iconDirections: {[expandState]: iconDirection}
    } = paneExpandButton[paneId];

    return (
      <Styled.Pane {...this.props} expandState={expandState}>
        {children}
        <ExpandButton
          onClick={this.handleExpand}
          iconDirection={iconDirection}
        />
      </Styled.Pane>
    );
  }
}

Pane.Header = Panel.Header;
Pane.Body = Styled.Body;
Pane.Footer = Styled.Footer;
