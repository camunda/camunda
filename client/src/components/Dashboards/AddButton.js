import React from 'react';

import {Modal, Button, Select, ControlGroup} from 'components';

import DashboardObject from './DashboardObject';
import {loadReports, getOccupiedTiles} from './service';

import './AddButton.css';

const size = {width: 3, height: 3};

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
      availableReports: reports,
      selectedReportId: (reports[0] && reports[0].id) || ''
    });
  }

  openModal = evt => {
    this.setState({
      modalOpen: true
    });
  }

  closeModal = evt => {
    this.setState({
      modalOpen: false
    });
  }

  selectReport = ({target:{value}}) => {
    this.setState({
      selectedReportId: value
    });
  }

  addReport = () => {
    this.closeModal();

    const position = this.getAddButtonPosition();

    this.props.addReport({
      position: {x: position.x, y: position.y},
      dimensions: {width: position.width, height: position.height},
      id: this.state.selectedReportId
    });
  }

  render() {
    const position = this.getAddButtonPosition();

    if(this.props.visible === false) {
      return null;
    }

    return <DashboardObject tileDimensions={this.props.tileDimensions} {...position}>
      <Button className='AddButton' onClick={this.openModal}>
        <div className='AddButton__symbol'></div>
        <div className='AddButton__text'>Add a Report</div>
      </Button>
      <Modal open={this.state.modalOpen} onClose={this.closeModal} className='AddButton__modal'>
        <Modal.Header>Add a Report</Modal.Header>
        <Modal.Content>
          <ControlGroup layout='centered'>
            <label htmlFor='AddButton__selectReports'>Select a Report from the list…</label>
            <Select value={this.state.selectedReportId} onChange={this.selectReport} name='AddButton__selectReports' className='AddButton__selectReports'>
              {this.state.availableReports.map(report => {
                return <Select.Option key={report.id} value={report.id}>{report.name}</Select.Option>
              })}
            </Select>
          </ControlGroup>
          <div className='AddButton__modal-divider'>or</div>
          <div className='AddButton__create-button'>
            <Button disabled={true}>Create new Report...</Button>
          </div>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.closeModal}>Cancel</Button>
          <Button type='primary' className='Button--blue' onClick={this.addReport}>Add Report</Button>
        </Modal.Actions>
      </Modal>

    </DashboardObject>;
  }

  getAddButtonPosition = () => {
    const occupiedTiles = getOccupiedTiles(this.props.reports);

    for(let y = 0;; y++) {
      for(let x = 0; x < this.props.tileDimensions.columns - size.width + 1; x++) {
        if(this.enoughSpaceForAddButton(occupiedTiles, x, y)) {
          return {x, y, ...size};
        }
      }
    }
  }

  enoughSpaceForAddButton(occupiedTiles, left, top) {
    for(let x = left; x < left + size.width; x++) {
      for(let y = top; y < top + size.height; y++) {
        if(occupiedTiles[x] && occupiedTiles[x][y]) {
          return false;
        }
      }
    }

    return true;
  }
}
