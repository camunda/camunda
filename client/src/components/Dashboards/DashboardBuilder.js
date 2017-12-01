import React from 'react';

import './DashboardBuilder.css';

import AddButton from './AddButton';
import DashboardReport from './DashboardReport';

const columns = 18;
const tileAspectRatio = 1;
const tileMargin = 8;
const rows = 9;

const addButtonSize = {width: 3, height: 3};

export default class DashboardBuilder extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      reports: []
    };
  }

  addReport = id => {
    const position = this.getAddButtonPosition();

    this.setState({
      reports: [
        ...this.state.reports,
        {
          position: {x: position.x, y: position.y},
          dimensions: {width: position.width, height: position.height},
          id
        }
      ]
    });
  }

  render() {
    const DashboardObject = this.DashboardObject;
    const addButtonPosition = this.getAddButtonPosition();

    return <div className='DashboardBuilder' ref={node => this.container = node}>
      <DashboardObject {...addButtonPosition}>
        <AddButton addReport={this.addReport} />
      </DashboardObject>
      {this.state.reports.map((report, idx) => <DashboardObject key={idx} {...report.position} {...report.dimensions}>
        <DashboardReport id={report.id} />
      </DashboardObject>)}
    </div>;
  }

  getAddButtonPosition = () => {
    const occupiedTiles = {};

    this.state.reports.forEach(({position, dimensions}) => {
      for(let x = position.x; x < position.x + dimensions.width; x++) {
        for(let y = position.y; y < position.y + dimensions.height; y++) {
          occupiedTiles[x] = occupiedTiles[x] || {};
          occupiedTiles[x][y] = true;
        }
      }
    });

    for(let y = 0; y < rows - addButtonSize.height + 1; y++) {
      for(let x = 0; x < columns - addButtonSize.width + 1; x++) {
        if(this.enoughSpaceForAddButton(occupiedTiles, x, y)) {
          return {x, y, ...addButtonSize};
        }
      }
    }
  }

  enoughSpaceForAddButton(occupiedTiles, left, top) {
    for(let x = left; x < left + addButtonSize.width; x++) {
      for(let y = top; y < top + addButtonSize.height; y++) {
        if(occupiedTiles[x] && occupiedTiles[x][y]) {
          return false;
        }
      }
    }

    return true;
  }

  componentDidMount() {
    const {innerWidth, outerWidth, innerHeight, outerHeight} = this.getTileDimensions();

    this.container.style.width = outerWidth * columns + 'px';
    this.container.style.height = outerHeight * rows + 'px';

    this.container.style.backgroundImage = `url("data:image/svg+xml;utf8,`+
      `<svg xmlns='http://www.w3.org/2000/svg' width='${outerWidth}' height='${outerHeight}'>` +
        `<rect stroke='rgba(0, 0, 0, 0.2)' stroke-width='1' fill='none' x='${tileMargin / 2}' y='${tileMargin / 2}' width='${innerWidth - 1}' height='${innerHeight - 1}'/>` +
      `</svg>")`;

    this.forceUpdate();
  }

  getTileDimensions = () => {
    const availableWidth = this.container.clientWidth;
    const outerWidth = ~~(availableWidth / columns); // make sure we are working with round values

    const innerWidth = outerWidth - tileMargin;
    const innerHeight = innerWidth / tileAspectRatio;

    const outerHeight = innerHeight + tileMargin;

    return {outerWidth, innerWidth, outerHeight, innerHeight};
  }

  DashboardObject = ({x, y, width, height, children}) => {
    if(this.container) {
      const {outerWidth, outerHeight} = this.getTileDimensions();

      return (<div style={{
        position: 'absolute',
        overflow: 'auto',
        backgroundColor: 'white',
        top: y * outerHeight + tileMargin / 2 - 1,
        left: x * outerWidth + tileMargin / 2 - 1,
        width: width * outerWidth - tileMargin + 1,
        height: height * outerHeight - tileMargin + 1,
        border: '1px solid lightgray'
      }}>
        {children}
      </div>);
    }

    return null;
  }
}
