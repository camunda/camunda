import React from 'react';

import {Button, DashboardObject} from 'components';

import {getOccupiedTiles} from '../service';

import ReportModal from './ReportModal';

import './AddButton.css';

const size = {width: 6, height: 4};

export default class AddButton extends React.Component {
  state = {open: false};

  openModal = () => {
    this.setState({
      open: true
    });
  };

  closeModal = () => {
    this.setState({
      open: false
    });
  };

  addReport = props => {
    this.closeModal();
    const position = this.getAddButtonPosition();

    const payload = {
      position: {x: position.x, y: position.y},
      dimensions: {width: position.width, height: position.height},
      ...props
    };

    this.props.addReport(payload);
  };

  render() {
    const position = this.getAddButtonPosition();

    if (this.props.visible === false) {
      return null;
    }

    return (
      <DashboardObject tileDimensions={this.props.tileDimensions} {...position}>
        <Button className="AddButton" onClick={this.openModal}>
          <div className="AddButton__symbol" />
          <div className="AddButton__text">Add a Report</div>
        </Button>
        {this.state.open && <ReportModal close={this.closeModal} confirm={this.addReport} />}
      </DashboardObject>
    );
  }

  getAddButtonPosition = () => {
    const occupiedTiles = getOccupiedTiles(this.props.reports);

    for (let y = 0; ; y++) {
      for (let x = 0; x < this.props.tileDimensions.columns - size.width + 1; x++) {
        if (this.enoughSpaceForAddButton(occupiedTiles, x, y)) {
          return {x, y, ...size};
        }
      }
    }
  };

  enoughSpaceForAddButton(occupiedTiles, left, top) {
    for (let x = left; x < left + size.width; x++) {
      for (let y = top; y < top + size.height; y++) {
        if (occupiedTiles[x] && occupiedTiles[x][y]) {
          return false;
        }
      }
    }

    return true;
  }
}
