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
    isCollapsed: PropTypes.bool,
    onCollapse: PropTypes.func.isRequired,
    expandButton: PropTypes.node.isRequired,
    collapseButton: PropTypes.node.isRequired,
    maxWidth: PropTypes.number.isRequired
  };

  static defaultProps = {
    isCollapsed: false
  };

  handleButtonClick = () => {
    this.props.onCollapse();
  };

  renderButton = button =>
    React.cloneElement(button, {
      onClick: this.handleButtonClick
    });

  render() {
    const {
      isCollapsed,
      children,
      collapseButton,
      expandButton,
      onCollapse,
      ...props
    } = this.props;

    return (
      <Styled.Collapsable {...props} isCollapsed={isCollapsed}>
        <Styled.ExpandedPanel isCollapsed={isCollapsed}>
          {this.renderButton(collapseButton)}
          {children}
        </Styled.ExpandedPanel>
        <Styled.CollapsedPanel isCollapsed={isCollapsed}>
          {this.renderButton(expandButton)}
        </Styled.CollapsedPanel>
      </Styled.Collapsable>
    );
  }
}

CollapsablePanel.Header = props => <Styled.Header {...props} />;

CollapsablePanel.Body = Panel.Body;

CollapsablePanel.Footer = Panel.Footer;
