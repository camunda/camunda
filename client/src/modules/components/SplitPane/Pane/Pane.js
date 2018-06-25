import React, {Children, cloneElement} from 'react';

import {withExpand} from 'modules/components/SplitPane/ExpandContext';
import ExpandButton from 'modules/components/ExpandButton';
import Panel from 'modules/components/Panel';

import * as Styled from './styled';

class Pane extends React.Component {
  handleExpand = () => {
    const {expand, resetExpanded, containerId, expandedId} = this.props;

    // (1) if there is an expanded panel, reset the expandedId
    // (2) otherwise expand the target id
    expandedId === null ? expand(containerId) : resetExpanded();
  };

  render() {
    const {containerId, expandedId} = this.props;

    const children = Children.map(this.props.children, child =>
      cloneElement(child, {containerId, expandedId})
    );

    const isExpanded = containerId === expandedId;

    return (
      <Styled.Pane {...this.props}>
        {children}
        <ExpandButton
          onClick={this.handleExpand}
          containerId={containerId}
          isExpanded={isExpanded}
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
