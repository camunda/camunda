import React from 'react';

import {Modal, Button, Select, ControlGroup, DashboardObject} from 'components';

import {loadReports, getOccupiedTiles} from '../service';

import './AddButton.css';

const size = {width: 6, height: 4};

export default class AddButton extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      modalOpen: false,
      availableReports: [],
      selectedReportId: ''
    };

    this.loadAvailableReports();
  }

  loadAvailableReports = async () => {
    const reports = await loadReports();

    this.setState({
      availableReports: reports
    });
  };

  openModal = evt => {
    this.setState({
      modalOpen: true,
      selectedReportId: ''
    });
  };

  closeModal = evt => {
    this.setState({
      modalOpen: false
    });
  };

  selectReport = ({target: {value}}) => {
    this.setState({
      selectedReportId: value
    });
  };

  addReport = () => {
    this.closeModal();

    const position = this.getAddButtonPosition();

    this.props.addReport({
      position: {x: position.x, y: position.y},
      dimensions: {width: position.width, height: position.height},
      id: this.state.selectedReportId
    });
  };

  render() {
    const position = this.getAddButtonPosition();

    if (this.props.visible === false) {
      return null;
    }

    const noReports = this.state.availableReports.length === 0;

    return (
      <DashboardObject tileDimensions={this.props.tileDimensions} {...position}>
        <Button className="AddButton" onClick={this.openModal}>
          <div className="AddButton__symbol" />
          <div className="AddButton__text">Add a Report</div>
        </Button>
        <Modal open={this.state.modalOpen} onClose={this.closeModal} className="AddButton__modal">
          <Modal.Header>Add a Report</Modal.Header>
          <Modal.Content>
            <ControlGroup layout="centered">
              <label htmlFor="AddButton__selectReports">Select a Report…</label>
              <Select
                disabled={noReports}
                value={this.state.selectedReportId}
                onChange={this.selectReport}
                name="AddButton__selectReports"
                className="AddButton__selectReports"
              >
                {this.renderPleaseSelectOption(!noReports)}
                {this.state.availableReports.map(report => {
                  return (
                    <Select.Option key={report.id} value={report.id}>
                      {this.truncate(report.name, 50)}
                    </Select.Option>
                  );
                })}
                {noReports ? <Select.Option>No reports created yet</Select.Option> : ''}
              </Select>
            </ControlGroup>
          </Modal.Content>
          <Modal.Actions>
            <Button onClick={this.closeModal}>Cancel</Button>
            <Button
              type="primary"
              color="blue"
              onClick={this.addReport}
              disabled={noReports || !this.state.selectedReportId}
            >
              Add Report
            </Button>
          </Modal.Actions>
        </Modal>
      </DashboardObject>
    );
  }

  truncate = (str, index) => {
    return str.length > index ? str.substr(0, index - 1) + '\u2026' : str;
  };

  renderPleaseSelectOption = hasReports => {
    if (hasReports) {
      return (
        <Select.Option defaultValue value="">
          Please select...
        </Select.Option>
      );
    } else {
      return '';
    }
  };

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
