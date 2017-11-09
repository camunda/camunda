import React from 'react';

import './ReportTile.css';

export default class ViewGrid extends React.Component {

  startDrag = () => {
    this.props.startDrag(this.props.data);
  }

  endDrag = () => {
    this.props.onDragEnd(this.props.data);
  }

  render () {

    const { gridSize, gridMargin, data} = this.props;

    const tileStyle = {
      top: (gridSize * data.position.x) + gridMargin + 'px',
      left: (gridSize * data.position.y) + gridMargin + 'px',
      width: gridSize * data.dimensions.width - gridMargin + 'px',
      height: gridSize * data.dimensions.height - gridMargin + 'px'
    };

    return (
      <div className={'report-tile'}
           onDragStart={this.startDrag.bind(this)}
           onDragEnd={this.endDrag.bind(this)}
           draggable="true"
           style={tileStyle}
      >

        <div className={'report-name-container'}>
          <div className={'report-name'}>{data.name}</div>
        </div>
      </div>)
  }
}
