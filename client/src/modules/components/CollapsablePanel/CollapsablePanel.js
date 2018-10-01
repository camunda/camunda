import React from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import * as Styled from './styled';

export default class CollapsablePanel extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ]),
    expandButton: PropTypes.node.isRequired,
    collapseButton: PropTypes.node.isRequired,
    maxWidth: PropTypes.number.isRequired
  };

  state = {
    isCollapsed: false
  };

  handleButtonClick = () => {
    const {isCollapsed: currentIsCollapsed} = this.state;
    this.setState({
      isCollapsed: !currentIsCollapsed
    });
  };

  renderButton = button =>
    React.cloneElement(button, {
      onClick: this.handleButtonClick
    });

  render() {
    const {children, collapseButton, expandButton, ...props} = this.props;
    const {isCollapsed} = this.state;

    return (
      <Styled.Collapsable {...props} isCollapsed={isCollapsed}>
        <Styled.Panel isCollapsed={isCollapsed}>
          {this.renderButton(collapseButton)}
          {children}
        </Styled.Panel>
        {!isCollapsed ? null : this.renderButton(expandButton)}
      </Styled.Collapsable>
    );
  }
}

CollapsablePanel.Header = Panel.Header;

CollapsablePanel.Body = Panel.Body;

CollapsablePanel.Footer = Panel.Footer;
