import React from 'react';

import './ReportTile.css';

export default class ViewGrid extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      gridMargin: this.props.gridMargin,
      gridSize: this.props.gridSize,
      report: this.props.data
    };
  }

  render () {
    const gridMargin = this.state.gridMargin;
    const gridSize = this.state.gridSize;

    const tileStyle = {
      top: (gridSize * this.state.report.position.x) + gridMargin + 'px',
      left: (gridSize * this.state.report.position.y) + gridMargin + 'px',
      width: gridSize * this.state.report.dimensions.width - gridMargin + 'px',
      height: gridSize * this.state.report.dimensions.height - gridMargin + 'px'
    };

    return (
      <div className={'report-tile'} style={tileStyle}>

        <div className={'report-name-container'}>
          <div className={'report-name'}>{this.state.report.name}</div>
        </div>
      </div>)
  }
}
