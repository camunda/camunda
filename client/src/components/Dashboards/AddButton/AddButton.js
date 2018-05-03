import React from 'react';

import {Modal, Button, Select, ControlGroup, DashboardObject, Input} from 'components';

import {loadReports, getOccupiedTiles} from '../service';

import './AddButton.css';

const size = {width: 6, height: 4};

export default class AddButton extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      modalOpen: false,
      availableReports: [],
      selectedReportId: '',
      externalSourceMode: false,
      externalSource: ''
    };
  }

  componentDidMount = async () => {
    const reports = await loadReports();

    this.setState({
      availableReports: reports
    });
  };

  openModal = evt => {
    this.setState({
      modalOpen: true,
      selectedReportId: '',
      externalSourceMode: false,
      externalSource: ''
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

    const payload = {
      position: {x: position.x, y: position.y},
      dimensions: {width: position.width, height: position.height}
    };

    if (this.state.externalSourceMode) {
      payload.id = '';
      payload.configuration = {external: this.state.externalSource};
    } else {
      payload.id = this.state.selectedReportId;
    }

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
        <Modal open={this.state.modalOpen} onClose={this.closeModal} className="AddButton__modal">
          <Modal.Header>Add a Report</Modal.Header>

          {this.state.externalSourceMode
            ? this.renderExternalSourceMode()
            : this.renderReportMode()}
          <Modal.Actions>
            <Button onClick={this.closeModal}>Cancel</Button>
            <Button
              type="primary"
              color="blue"
              onClick={this.addReport}
              disabled={!this.isAddButtonEnabled()}
            >
              Add Report
            </Button>
          </Modal.Actions>
        </Modal>
      </DashboardObject>
    );
  }

  isAddButtonEnabled = () => {
    const {externalSourceMode, externalSource, selectedReportId} = this.state;
    return (externalSourceMode && externalSource) || (!externalSourceMode && selectedReportId);
  };

  renderReportMode = () => {
    const noReports = this.state.availableReports.length === 0;
    return (
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
        <p
          className="AddButton__externalSourceLink"
          onClick={() =>
            this.setState({
              externalSourceMode: true
            })
          }
        >
          Add External Source
        </p>
      </Modal.Content>
    );
  };

  renderExternalSourceMode = () => (
    <Modal.Content>
      <ControlGroup layout="centered">
        <label htmlFor="AddButton__externalSourceInput">
          Enter URL of external datasource to be included on the dashboard
        </label>
        <Input
          name="AddButton__externalSourceInput"
          className="AddButton__externalSourceInput"
          placeholder="https://www.example.com/widget/embed.html"
          value={this.state.externalSource}
          onChange={({target: {value}}) =>
            this.setState({
              externalSource: value
            })
          }
        />
      </ControlGroup>
    </Modal.Content>
  );

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
