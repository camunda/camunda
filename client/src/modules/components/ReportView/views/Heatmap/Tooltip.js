import React from 'react';

import {addDiagramTooltip} from './service';

export default class Tooltip extends React.Component {

  render() {
    return null;
  }

  componentDidMount() {
    this.props.viewer.get('eventBus').on('element.hover', this.renderTooltip);
  }

  componentWillUnmount() {
    this.props.viewer.get('eventBus').off('element.hover', this.renderTooltip);
  }

  renderTooltip = ({element: {id}}) => {
    const {viewer} = this.props;

    this.removeOverlays(viewer);
    const value = this.props.data[id];
    if (value !== undefined) {
      addDiagramTooltip(viewer, id, this.props.formatter(value));
    }
  };

  removeOverlays = (viewer) => {
    viewer.get('overlays').clear();
  }


}
