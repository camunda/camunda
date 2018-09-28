import React, {Children, cloneElement} from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import {PANE_ID, EXPAND_STATE, DIRECTION} from 'modules/constants';

import * as Styled from './styled';

export default class Pane extends React.Component {
  static propTypes = {
    handleExpand: PropTypes.func,
    paneId: PropTypes.oneOf(Object.values(PANE_ID)),
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  handleTopExpand = () => {
    this.props.handleExpand(PANE_ID.TOP);
  };

  handleBottomExpand = () => {
    this.props.handleExpand(PANE_ID.BOTTOM);
  };

  renderTopPane = () => {
    const {expandState} = this.props;

    const children = Children.map(this.props.children, child =>
      cloneElement(child, {expandState})
    );

    return (
      <Styled.Pane {...this.props} expandState={expandState}>
        {children}
      </Styled.Pane>
    );
  };

  renderBottomPane = () => {
    const {expandState} = this.props;

    const children = Children.map(this.props.children, child =>
      cloneElement(child, {expandState})
    );

    const expandBottomButton = (
      <Styled.PaneExpandButton
        onClick={this.handleBottomExpand}
        direction={DIRECTION.UP}
      />
    );
    const expandTopButton = (
      <Styled.PaneExpandButton
        onClick={this.handleTopExpand}
        direction={DIRECTION.DOWN}
      />
    );

    let buttons;

    switch (expandState) {
      case EXPAND_STATE.EXPANDED:
        buttons = expandTopButton;
        break;
      case EXPAND_STATE.COLLAPSED:
        buttons = expandBottomButton;
        break;
      default:
        buttons = (
          <React.Fragment>
            {expandTopButton}
            {expandBottomButton}
          </React.Fragment>
        );
    }

    return (
      <Styled.Pane {...this.props} expandState={expandState}>
        {children}
        <Styled.ButtonsContainer>{buttons}</Styled.ButtonsContainer>
      </Styled.Pane>
    );
  };

  render() {
    return this.props.paneId === PANE_ID.TOP
      ? this.renderTopPane()
      : this.renderBottomPane();
  }
}

Pane.Header = Panel.Header;
Pane.Body = Styled.Body;
Pane.Footer = Styled.Footer;
