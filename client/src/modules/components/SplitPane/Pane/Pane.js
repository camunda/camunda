import React, {Children, cloneElement} from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import {ICON_DIRECTION} from 'modules/components/ExpandButton/constants';

import {PANE_ID, PANE_STATE} from './constants';
import * as Styled from './styled';

const paneExpandButton = {
  [PANE_ID.TOP]: {
    ExpandButton: Styled.TopExpandButton,
    // iconDirections: {EXPANDED, NOT_EXPANDED}
    iconDirections: {true: ICON_DIRECTION.UP, false: ICON_DIRECTION.DOWN}
  },
  [PANE_ID.BOTTOM]: {
    ExpandButton: Styled.BottomExpandButton,
    // iconDirections: {EXPANDED, NOT_EXPANDED}
    iconDirections: {true: ICON_DIRECTION.DOWN, false: ICON_DIRECTION.UP}
  }
};
export default class Pane extends React.Component {
  static propTypes = {
    expand: PropTypes.func,
    paneId: PropTypes.oneOf(Object.values(PANE_ID)),
    paneState: PropTypes.oneOf(Object.values(PANE_STATE)),
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  handleExpand = () => {
    this.props.expand(this.props.paneId);
  };

  render() {
    const {paneState, paneId} = this.props;

    const children = Children.map(this.props.children, child =>
      cloneElement(child, {paneState})
    );

    const isExpanded = paneState === PANE_STATE.EXPANDED;

    const {
      ExpandButton,
      iconDirections: {[isExpanded]: iconDirection}
    } = paneExpandButton[paneId];

    return (
      <Styled.Pane {...this.props} paneState={paneState}>
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
