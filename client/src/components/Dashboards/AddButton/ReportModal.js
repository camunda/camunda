import React from 'react';

import {Modal, Button, Select, ControlGroup} from 'components';

import {loadReports} from '../service';

import './ReportModal.css';

export default class ReportModal extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      availableReports: null,
      selectedReportId: ''
    };
  }

  componentDidMount = async () => {
    const reports = await loadReports();

    this.setState({
      availableReports: reports
    });
  };

  selectReport = ({target: {value}}) => {
    this.setState({
      selectedReportId: value
    });
  };

  addReport = () => {
    this.props.confirm({id: this.state.selectedReportId});
  };

  render() {
    const noReports = !this.state.availableReports || this.state.availableReports.length === 0;
    const loading = this.state.availableReports === null;

    return (
      <Modal
        open
        onClose={this.props.close}
        onEnterPress={this.state.selectedReportId ? this.addReport : undefined}
      >
        <Modal.Header>Add a Report</Modal.Header>
        <Modal.Content>
          <ControlGroup layout="centered">
            <label htmlFor="ReportModal__selectReports">Select a Report…</label>
            <Select
              disabled={noReports || loading}
              value={this.state.selectedReportId}
              onChange={this.selectReport}
              name="ReportModal__selectReports"
              className="ReportModal__selectReports"
            >
              {this.renderPleaseSelectOption(!noReports)}
              {!loading &&
                this.state.availableReports.map(report => {
                  return (
                    <Select.Option key={report.id} value={report.id}>
                      {this.truncate(report.name, 50)}
                    </Select.Option>
                  );
                })}
              {loading ? (
                <Select.Option>loading...</Select.Option>
              ) : noReports ? (
                <Select.Option>No reports created yet</Select.Option>
              ) : (
                ''
              )}
            </Select>
          </ControlGroup>
          <p className="ReportModal__externalSourceLink" onClick={this.props.gotoExternalMode}>
            Add External Source
          </p>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.close}>Cancel</Button>
          <Button
            type="primary"
            color="blue"
            onClick={this.addReport}
            disabled={!this.state.selectedReportId}
          >
            Add Report
          </Button>
        </Modal.Actions>
      </Modal>
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
}
