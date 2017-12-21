import React from 'react';

import {addDiagramTooltip} from './service';

export default class Tooltip extends React.Component {

  render() {
    return null;
  }

  componentDidMount() {
    this.renderTooltip();
  }

  componentDidUpdate(nextProps, nextState) {
    this.renderTooltip();
  }

  renderTooltip = () => {
    const {viewer, data} = this.props;
    viewer.get('eventBus').on('element.hover', ({element: {id}}) => {

      this.removeOverlays(viewer);
      const value = data[id];
      if (value !== undefined) {
        addDiagramTooltip(viewer, id, value);
      }
    });
  }

  removeOverlays = (viewer) => {
    viewer.get('overlays').clear();
  }
  
  
}
