/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Children, cloneElement, useContext} from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import {CollapsablePanelConsumer} from 'modules/contexts/CollapsablePanelContext';
import {PANE_ID, EXPAND_STATE, DIRECTION} from 'modules/constants';

import * as Styled from './styled';

const paneContext = React.createContext();

export default class Pane extends React.Component {
  static propTypes = {
    handleExpand: PropTypes.func,
    paneId: PropTypes.oneOf(Object.values(PANE_ID)),
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ]),
    hasShiftableControls: PropTypes.bool,
    titles: PropTypes.shape({top: PropTypes.string, bottom: PropTypes.string})
  };

  static defaultProps = {
    hasShiftableControls: false,
    titles: {top: 'Top', bottom: 'Bottom'}
  };

  handleTopExpand = () => {
    this.props.handleExpand(PANE_ID.TOP);
  };

  handleBottomExpand = () => {
    this.props.handleExpand(PANE_ID.BOTTOM);
  };

  getChildren = () => {
    const {expandState} = this.props;

    const children = Children.map(this.props.children, child =>
      cloneElement(child, {expandState})
    );

    return children;
  };

  getBottomPaneButtons = () => {
    const {
      expandState,
      titles: {top, bottom}
    } = this.props;

    const isTopButtonVisible = expandState !== EXPAND_STATE.COLLAPSED;
    const isBottomButtonVisible = expandState !== EXPAND_STATE.EXPANDED;

    return (
      <CollapsablePanelConsumer>
        {context => (
          <Styled.ButtonsContainer
            isShifted={
              this.props.hasShiftableControls
                ? !context.isSelectionsCollapsed
                : false
            }
          >
            {isTopButtonVisible && (
              <Styled.PaneExpandButton
                onClick={this.handleTopExpand}
                direction={DIRECTION.DOWN}
                title={`Expand ${top}`}
              />
            )}
            {isBottomButtonVisible && (
              <Styled.PaneExpandButton
                onClick={this.handleBottomExpand}
                direction={DIRECTION.UP}
                title={`Expand ${bottom}`}
              />
            )}
          </Styled.ButtonsContainer>
        )}
      </CollapsablePanelConsumer>
    );
  };

  render() {
    const {hasShiftableControls, children, ...otherProps} = this.props;

    return (
      <Styled.Pane {...otherProps} expandState={this.props.expandState}>
        <paneContext.Provider value={{expandState: otherProps.expandState}}>
          {this.props.paneId === PANE_ID.BOTTOM && this.getBottomPaneButtons()}
          {children}
        </paneContext.Provider>
      </Styled.Pane>
    );
  }
}

Pane.Header = function PaneHeader(props) {
  const {expandState} = useContext(paneContext);
  return <Panel.Header {...props} expandState={expandState} />;
};

Pane.Body = function PaneBody(props) {
  const {expandState} = useContext(paneContext);

  return <Styled.Body {...props} expandState={expandState} />;
};

Pane.Footer = function PaneFooter(props) {
  const {expandState} = useContext(paneContext);
  return <Styled.Footer {...props} expandState={expandState} />;
};
