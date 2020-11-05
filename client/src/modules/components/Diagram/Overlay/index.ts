/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {createPortal} from 'react-dom';

type Props = {
  onOverlayAdd: () => void;
  onOverlayClear: () => void;
  isViewerLoaded: boolean;
  id: string;
  type: string;
  position: unknown;
  children?: React.ReactNode;
};

export default class Overlay extends React.PureComponent<Props, {}> {
  domElement = document.createElement('div');

  componentDidMount() {
    this.props.isViewerLoaded && this.addOverlay();
  }

  componentDidUpdate(prevProps: Props) {
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

    // @ts-expect-error ts-migrate(2339) FIXME: Property 'onOverlayAdd' does not exist on type 'Re... Remove this comment to see the full error message
    this.props.onOverlayAdd(id, type, {
      position,
      html: this.domElement,
    });
  };

  clearOverlay = () => {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'onOverlayClear' does not exist on type '... Remove this comment to see the full error message
    this.props.onOverlayClear({element: this.domElement});
  };

  render() {
    return createPortal(this.props.children, this.domElement);
  }
}
