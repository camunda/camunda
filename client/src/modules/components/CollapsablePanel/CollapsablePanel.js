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
    onCollapse: PropTypes.func,
    expandButton: PropTypes.node.isRequired,
    collapseButton: PropTypes.node.isRequired,
    maxWidth: PropTypes.number.isRequired,
    type: PropTypes.oneOf(['selections', 'filters']).isRequired
  };

  static defaultProps = {
    isCollapsed: false
  };

  constructor(props) {
    super(props);
    this.state = {isCollapsed: props.isCollapsed};
  }

  handleButtonClick = () => {
    if (this.props.onCollapse) {
      this.props.onCollapse();
    }

    this.setState(prevState => {
      return {
        isCollapsed: !prevState.isCollapsed
      };
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
        <Styled.ExpandedPanel isCollapsed={isCollapsed} isRounded>
          {this.renderButton(collapseButton)}
          {children}
        </Styled.ExpandedPanel>
        <Styled.CollapsedPanel isCollapsed={isCollapsed} isRounded>
          {this.renderButton(expandButton)}
        </Styled.CollapsedPanel>
      </Styled.Collapsable>
    );
  }
}

CollapsablePanel.Header = Panel.Header;

CollapsablePanel.Body = Panel.Body;

CollapsablePanel.Footer = Panel.Footer;
