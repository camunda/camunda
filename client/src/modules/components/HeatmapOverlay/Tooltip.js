/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {addDiagramTooltip} from './service';

export default class Tooltip extends React.Component {
  state = {
    openOverlayId: null,
  };

  render() {
    return null;
  }

  componentDidMount() {
    const {viewer, alwaysShow} = this.props;
    viewer.get('eventBus').on('element.hover', this.renderTooltip);
    if (alwaysShow) {
      this.addAllTooltips();
    }
  }

  componentDidUpdate(prevProps) {
    const {viewer, alwaysShow} = this.props;

    if (prevProps.alwaysShow && !alwaysShow) {
      this.removeAllOverlays(viewer);
    }

    if (alwaysShow) {
      this.addAllTooltips();
    }
  }

  addAllTooltips = () => {
    const {viewer, formatter, data} = this.props;
    Object.keys(data).forEach((id) => {
      addDiagramTooltip({
        viewer,
        element: id,
        tooltipContent: formatter(data[id], id),
        theme: this.props.theme,
      });
    });
  };

  componentWillUnmount() {
    this.props.viewer.get('eventBus').off('element.hover', this.renderTooltip);
    this.removeAllOverlays(this.props.viewer);
  }

  renderTooltip = async ({element: {id}}) => {
    const {viewer, alwaysShow} = this.props;

    if (alwaysShow) {
      return;
    }

    const value = this.props.data[id];
    if (value !== undefined) {
      this.removeAllOverlays(viewer);
      this.setState({
        openOverlayId: await addDiagramTooltip({
          viewer,
          element: id,
          tooltipContent: this.props.formatter(value, id),
          theme: this.props.theme,
          onMouseEnter: this.onMouseEnter,
          onMouseLeave: this.onMouseLeave,
        }),
      });
    } else {
      this.scheduleRemoveOverlay(viewer, this.state.openOverlayId);
    }
  };

  onMouseEnter = () => {
    clearTimeout(this.scheduledRemove);
  };

  onMouseLeave = () => {
    this.removeAllOverlays(this.props.viewer);
    this.setState({openOverlayId: null});
  };

  scheduleRemoveOverlay = (viewer, openOverlayId) => {
    if (openOverlayId) {
      this.scheduledRemove = setTimeout(() => viewer.get('overlays').remove(openOverlayId), 300);
    }
  };

  removeAllOverlays = (viewer) => {
    viewer.get('overlays').remove({type: 'TOOLTIP'});
  };
}
