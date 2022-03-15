/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Children, cloneElement, useContext} from 'react';
import {isEqual} from 'lodash';

import {Panel} from 'modules/components/Panel';
import {PANE_ID, EXPAND_STATE} from 'modules/constants';

import * as Styled from './styled';

// @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
const paneContext = React.createContext();

type OwnProps = {
  handleExpand?: (...args: any[]) => any;
  paneId?: 'TOP' | 'BOTTOM' | 'LEFT' | 'RIGHT';
  expandState?: 'DEFAULT' | 'EXPANDED' | 'COLLAPSED';
  hasShiftableControls?: boolean;
  titles?: {
    top?: string;
    bottom?: string;
  };
};

type Props = OwnProps & typeof Pane.defaultProps;

class Pane extends React.Component<Props> {
  static Header: any;
  static Body: any;
  static Footer: any;

  static defaultProps = {
    hasShiftableControls: false,
    titles: {top: 'Top', bottom: 'Bottom'},
  };

  bottomButton: any;
  bottomButtonRef: any;
  topButton: any;
  topButtonRef: any;

  constructor(props: Props) {
    super(props);
    const {
      titles: {top, bottom},
    } = props;
    this.topButtonRef = React.createRef();
    this.topButton = (
      <Styled.PaneCollapseButton
        onClick={this.handleTopExpand}
        direction="DOWN"
        title={`Expand ${top}`}
        ref={this.topButtonRef}
      />
    );
    this.bottomButtonRef = React.createRef();
    this.bottomButton = (
      <Styled.PaneCollapseButton
        onClick={this.handleBottomExpand}
        direction="UP"
        title={`Expand ${bottom}`}
        ref={this.bottomButtonRef}
      />
    );
  }

  componentDidUpdate(prevProps: Props, prevState: any, snapshot: any) {
    const expandState = this.props.expandState;
    if (!isEqual(prevProps.expandState, expandState)) {
      const topButtonCurrent = this.topButtonRef.current;
      const bottomButtonCurrent = this.bottomButtonRef.current;
      if (
        isEqual(expandState, EXPAND_STATE.EXPANDED) &&
        topButtonCurrent != null
      ) {
        topButtonCurrent.focus();
      } else if (
        isEqual(expandState, EXPAND_STATE.COLLAPSED) &&
        bottomButtonCurrent != null
      ) {
        bottomButtonCurrent.focus();
      }
    }
  }

  handleTopExpand = () => {
    // @ts-expect-error ts-migrate(2722) FIXME: Cannot invoke an object which is possibly 'undefin... Remove this comment to see the full error message
    this.props.handleExpand(PANE_ID.TOP);
  };

  handleBottomExpand = () => {
    // @ts-expect-error ts-migrate(2722) FIXME: Cannot invoke an object which is possibly 'undefin... Remove this comment to see the full error message
    this.props.handleExpand(PANE_ID.BOTTOM);
  };

  getChildren = () => {
    const {expandState} = this.props;

    const children = Children.map(this.props.children, (child) =>
      // @ts-expect-error ts-migrate(2769) FIXME: Type 'undefined' is not assignable to type 'ReactE... Remove this comment to see the full error message
      cloneElement(child, {expandState})
    );

    return children;
  };

  getBottomPaneButtons = () => {
    const {expandState} = this.props;

    const isTopButtonVisible = expandState !== EXPAND_STATE.COLLAPSED;
    const isBottomButtonVisible = expandState !== EXPAND_STATE.EXPANDED;

    return (
      <Styled.ButtonsContainer>
        {isTopButtonVisible && this.topButton}
        {isBottomButtonVisible && this.bottomButton}
      </Styled.ButtonsContainer>
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

Pane.Header = function PaneHeader(props: any) {
  // @ts-expect-error ts-migrate(2339) FIXME: Property 'expandState' does not exist on type 'unk... Remove this comment to see the full error message
  const {expandState} = useContext(paneContext) || {expandState: 'DEFAULT'};
  return <Panel.Header {...props} expandState={expandState} />;
};

Pane.Body = function PaneBody(props: any) {
  // @ts-expect-error ts-migrate(2339) FIXME: Property 'expandState' does not exist on type 'unk... Remove this comment to see the full error message
  const {expandState} = useContext(paneContext) || {expandState: 'DEFAULT'};

  return <Styled.Body {...props} expandState={expandState} />;
};

Pane.Footer = function PaneFooter(props: any) {
  // @ts-expect-error ts-migrate(2339) FIXME: Property 'expandState' does not exist on type 'unk... Remove this comment to see the full error message
  const {expandState} = useContext(paneContext) || {expandState: 'DEFAULT'};
  return <Styled.Footer {...props} expandState={expandState} />;
};

export default Pane;
