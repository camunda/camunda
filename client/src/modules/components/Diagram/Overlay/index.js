/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {createPortal} from 'react-dom';
import PropTypes from 'prop-types';

export default class Overlay extends React.PureComponent {
  static propTypes = {
    onOverlayAdd: PropTypes.func.isRequired,
    onOverlayClear: PropTypes.func.isRequired,
    isViewerLoaded: PropTypes.bool.isRequired,
    id: PropTypes.string.isRequired,
    type: PropTypes.string.isRequired,
    position: PropTypes.object.isRequired,
    children: PropTypes.node,
  };

  domElement = document.createElement('div');

  componentDidMount() {
    this.props.isViewerLoaded && this.addOverlay();
  }

  componentDidUpdate(prevProps) {
    // if the selected flownode or there is a new viewer, reattach the popover
    const shouldReattachToDiagram =
      this.props.id !== prevProps.id ||
      this.props.isViewerLoaded !== prevProps.isViewerLoaded;

    if (shouldReattachToDiagram) {
      this.clearOverlay();
      this.props.isViewerLoaded && this.addOverlay();
    }
  }

  componentWillUnmount() {
    this.clearOverlay();
  }

  addOverlay = () => {
    const {id, type, position} = this.props;

    this.props.onOverlayAdd(id, type, {
      position,
      html: this.domElement,
    });
  };

  clearOverlay = () => {
    this.props.onOverlayClear({element: this.domElement});
  };

  render() {
    return createPortal(this.props.children, this.domElement);
  }
}
