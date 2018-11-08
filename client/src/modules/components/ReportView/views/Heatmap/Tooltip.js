import React from 'react';

import {addDiagramTooltip} from './service';

export default class Tooltip extends React.Component {
  render() {
    return null;
  }

  componentDidMount() {
    this.props.viewer.get('eventBus').on('element.hover', this.renderTooltip);
    const alwaysShow = !this.props.hideAbsoluteValue || !this.props.hideRelativeValue;
    if (alwaysShow) {
      this.addAllTooltips();
    }
  }

  componentDidUpdate(prevProps) {
    const {viewer, hideAbsoluteValue, hideRelativeValue, data} = this.props;

    if (
      prevProps.hideRelativeValue !== hideRelativeValue ||
      prevProps.hideAbsoluteValue !== hideAbsoluteValue ||
      data !== prevProps.data
    ) {
      this.removeOverlays(viewer);
      if (!hideAbsoluteValue || !hideRelativeValue) {
        this.addAllTooltips();
      }
    }
  }

  addAllTooltips = () => {
    const {viewer, formatter, data} = this.props;
    Object.keys(data).forEach(id => {
      addDiagramTooltip(viewer, id, formatter(data[id], id));
    });
  };

  componentWillUnmount() {
    this.props.viewer.get('eventBus').off('element.hover', this.renderTooltip);
    this.removeOverlays(this.props.viewer);
  }

  renderTooltip = ({element: {id}}) => {
    const alwaysShow = !this.props.hideAbsoluteValue || !this.props.hideRelativeValue;
    if (alwaysShow) {
      return;
    }
    const {viewer} = this.props;

    this.removeOverlays(viewer);
    const value = this.props.data[id];
    if (value !== undefined) {
      addDiagramTooltip(viewer, id, this.props.formatter(value, id));
    }
  };

  removeOverlays = viewer => {
    viewer.get('overlays').remove({type: 'TOOLTIP'});
  };
}
