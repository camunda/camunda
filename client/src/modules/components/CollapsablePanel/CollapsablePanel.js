/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

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

  constructor(props) {
    super(props);
    this.expandButtonRef = React.createRef();
    this.collapseButtonRef = React.createRef();
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    if (this.props.isCollapsed !== prevProps.isCollapsed) {
      if (this.props.isCollapsed) {
        setTimeout(() => this.expandButtonRef.current.focus(), 200);
      } else {
        setTimeout(() => this.collapseButtonRef.current.focus(), 200);
      }
    }
  }

  handleButtonClick = () => {
    this.props.onCollapse();
  };

  renderCollapseButton = () =>
    React.cloneElement(this.props.collapseButton, {
      onClick: this.handleButtonClick,
      ref: this.collapseButtonRef
    });

  renderExpandButton = () =>
    React.cloneElement(this.props.expandButton, {
      onClick: this.handleButtonClick,
      ref: this.expandButtonRef
    });

  render() {
    const {isCollapsed, children} = this.props;

    return (
      <Styled.Collapsable {...this.props} isCollapsed={isCollapsed}>
        <Styled.ExpandedPanel isCollapsed={isCollapsed}>
          {this.renderCollapseButton()}
          {children}
        </Styled.ExpandedPanel>
        <Styled.CollapsedPanel isCollapsed={isCollapsed}>
          {this.renderExpandButton()}
        </Styled.CollapsedPanel>
      </Styled.Collapsable>
    );
  }
}

CollapsablePanel.Header = props => <Styled.Header {...props} />;

CollapsablePanel.Body = Panel.Body;

CollapsablePanel.Footer = Panel.Footer;
