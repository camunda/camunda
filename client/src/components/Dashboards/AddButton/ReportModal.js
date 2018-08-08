import React from 'react';

import {Modal, Button, Select, ControlGroup, Input, ErrorMessage} from 'components';

import {loadReports} from '../service';

import './ReportModal.css';

export default class ReportModal extends React.Component {
  state = {
    availableReports: null,
    selectedReportId: '',
    external: false,
    externalUrl: ''
  };

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
    const {external, selectedReportId, externalUrl} = this.state;
    this.props.confirm(
      external ? {id: '', configuration: {external: externalUrl}} : {id: selectedReportId}
    );
  };

  toggleExternal = () => {
    this.setState(({external}) => {
      return {
        external: !external,
        externalUrl: '',
        selectedReportId: ''
      };
    });
  };

  isValid = url => {
    // url has to start with https:// or http://
    return url.match(/^(https|http):\/\/.+/);
  };

  render() {
    const noReports = !this.state.availableReports || this.state.availableReports.length === 0;
    const loading = this.state.availableReports === null;

    const {external, externalUrl, selectedReportId, availableReports} = this.state;
    const isInvalidExternal = !this.isValid(externalUrl);

    const isInvalid = external ? isInvalidExternal : !selectedReportId;

    return (
      <Modal
        className="ReportModal"
        open
        onClose={this.props.close}
        onConfirm={!isInvalid ? this.addReport : undefined}
      >
        <Modal.Header>Add a Report</Modal.Header>
        <Modal.Content>
          <ControlGroup layout="centered">
            <label htmlFor="ReportModal__selectReports">Select a Report…</label>
            <Select
              disabled={noReports || loading || external}
              value={selectedReportId}
              onChange={this.selectReport}
              name="ReportModal__selectReports"
              className="ReportModal__selectReports"
            >
              {this.renderPleaseSelectOption(!noReports)}
              {!loading &&
                availableReports.map(report => {
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
          <p className="ReportModal__externalSourceLink" onClick={this.toggleExternal}>
            {external ? 'Add Optimize Report' : 'Add External Source'}
          </p>
          {external && (
            <ControlGroup>
              <label htmlFor="externalInput">
                Enter URL of external datasource to be included on the dashboard
              </label>
              <Input
                name="externalInput"
                className="externalInput"
                placeholder="https://www.example.com/widget/embed.html"
                value={externalUrl}
                isInvalid={isInvalidExternal}
                onChange={({target: {value}}) =>
                  this.setState({
                    externalUrl: value
                  })
                }
              />
              {isInvalidExternal && (
                <ErrorMessage className="ExternalModal__error">
                  URL has to start with http:// or https://
                </ErrorMessage>
              )}
            </ControlGroup>
          )}
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.close}>Cancel</Button>
          <Button type="primary" color="blue" onClick={this.addReport} disabled={isInvalid}>
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
