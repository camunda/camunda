import React, {Children, cloneElement} from 'react';
import PropTypes from 'prop-types';

import {withExpand} from 'modules/components/SplitPane/ExpandContext';
import Panel from 'modules/components/Panel';
import {ICON_DIRECTION} from 'modules/components/ExpandButton/constants';

import {PANE_ID} from './constants';
import * as Styled from './styled';

// iconDirections: {EXPANDED, NOT_EXPANDED}
const paneExpandButton = {
  [PANE_ID.TOP]: {
    ExpandButton: Styled.TopExpandButton,
    iconDirections: {true: ICON_DIRECTION.UP, false: ICON_DIRECTION.DOWN}
  },
  [PANE_ID.BOTTOM]: {
    ExpandButton: Styled.BottomExpandButton,
    iconDirections: {true: ICON_DIRECTION.DOWN, false: ICON_DIRECTION.UP}
  }
};
class Pane extends React.Component {
  static propTypes = {
    expand: PropTypes.func.isRequired,
    resetExpanded: PropTypes.func.isRequired,
    paneId: PropTypes.string.isRequired,
    expandedId: PropTypes.string,
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

    const children = Children.map(this.props.children, child =>
      cloneElement(child, {paneId, expandedId})
    );

    const isExpanded = expandedId === paneId;

    const {
      ExpandButton,
      iconDirections: {[isExpanded]: iconDirection}
    } = paneExpandButton[paneId];

    return (
      <Styled.Pane {...this.props}>
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
WithExpandPane.Body = Styled.Body;
WithExpandPane.Footer = Styled.Footer;

export default WithExpandPane;
