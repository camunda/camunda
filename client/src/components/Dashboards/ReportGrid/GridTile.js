import React from 'react';

import './GridTile.css'

export default class GridTile extends React.Component {

  processLeave = (event) => {
    event.preventDefault();

    this.props.highlightOut(
      Number.parseInt(this.props.row, 10),
      Number.parseInt(this.props.col, 10)
    );
  }

  processOver = (event) => {
    event.preventDefault();
    this.props.highlightIn(
      Number.parseInt(this.props.row, 10),
      Number.parseInt(this.props.col, 10)
    );
  }

  processDrop = (event) => {
    this.props.reportDroped({
      row: Number.parseInt(this.props.row, 10),
      col: Number.parseInt(this.props.col, 10),
      target: event.target
    });
  }

  render() {
    const { gridSize, gridMargin, row, col, highlighted, inConflict} = this.props;

    let cellStyle = {
      top: (gridSize * row) + gridMargin + 'px',
      left: (gridSize * col) + gridMargin + 'px',
      width: gridSize - gridMargin + 'px',
      height: gridSize - gridMargin + 'px'
    };

    let className = 'grid-tile';
    if (highlighted) {
      className = className + ' grid-tile--highlight'
    }

    if (inConflict) {
      className = className + ' grid-tile--conflict'
    }

    return (
      <div
        row={row}
        col={col}
        className={className}
        onDragLeave={this.processLeave.bind(this)}
        onDragOver={this.processOver.bind(this)}
        onDrop={this.processDrop.bind(this)}
        droppable="true"
        style={cellStyle}></div>
    )

  }
}
