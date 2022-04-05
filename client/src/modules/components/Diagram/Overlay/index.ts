/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {OverlayType} from 'modules/types/modeler';
import React from 'react';
import {createPortal} from 'react-dom';

type Props = {
  onOverlayAdd: (id: string, type: string, overlay: OverlayType) => void;
  onOverlayClear: ({element}: {element: HTMLDivElement}) => void;
  isViewerLoaded: boolean;
  id: string;
  type: string;
  position: OverlayType['position'];
  children?: React.ReactNode;
};

class Overlay extends React.PureComponent<Props, {}> {
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

export {Overlay};
