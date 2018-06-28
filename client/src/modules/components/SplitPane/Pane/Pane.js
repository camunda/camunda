import React, {Children, cloneElement} from 'react';
import PropTypes from 'prop-types';

import {withExpand} from 'modules/components/SplitPane/ExpandContext';
import Panel from 'modules/components/Panel';
import {ICON_DIRECTION} from 'modules/components/ExpandButton/constants';

import {PANE_ID} from './constants';
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
class Pane extends React.Component {
  static propTypes = {
    expand: PropTypes.func.isRequired,
    resetExpanded: PropTypes.func.isRequired,
    paneId: PropTypes.oneOf(Object.values(PANE_ID)).isRequired,
    expandedId: PropTypes.oneOf([...Object.values(PANE_ID), null]),
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  handleExpand = () => {
    const {expand, resetExpanded, paneId, expandedId} = this.props;

    // (1) if there is an expanded panel, reset the expandedId
    // (2) otherwise expand the target id
    expandedId === null ? expand(paneId) : resetExpanded();
  };

  render() {
    const {paneId, expandedId} = this.props;

    const isExpanded = expandedId === paneId;

    const {
      ExpandButton,
      iconDirections: {[isExpanded]: iconDirection}
    } = paneExpandButton[paneId];

    const isCollapsed = Boolean(expandedId && expandedId !== paneId);

    const children = Children.map(this.props.children, child =>
      cloneElement(child, {isCollapsed})
    );

    return (
      <Styled.Pane {...this.props} isCollapsed={isCollapsed}>
        {children}
        <ExpandButton
          onClick={this.handleExpand}
          iconDirection={iconDirection}
        />
      </Styled.Pane>
    );
  }
}

const WithExpandPane = withExpand(Pane);
WithExpandPane.Header = Panel.Header;
WithExpandPane.Body = withExpand(Styled.Body);
WithExpandPane.Footer = withExpand(Styled.Footer);

export default WithExpandPane;
