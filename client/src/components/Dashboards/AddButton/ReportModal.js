/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {
  Modal,
  Button,
  Select,
  ControlGroup,
  Input,
  ErrorMessage,
  Typeahead,
  LoadingIndicator,
  Labeled
} from 'components';

import {loadEntities} from 'services';

import './ReportModal.scss';

export default class ReportModal extends React.Component {
  state = {
    availableReports: null,
    selectedReportId: '',
    external: false,
    externalUrl: '',
    error: false
  };

  componentDidMount = async () => {
    const reports = await loadEntities('report', 'lastModified');

    this.setState({
      availableReports: reports
    });
  };

  selectReport = ({id}) => {
    this.setState({
      selectedReportId: id
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
            {!loading && !noReports && (
              <Typeahead
                disabled={noReports || loading || external}
                placeholder="Select a Report"
                values={availableReports}
                onSelect={this.selectReport}
                formatter={({name}) => this.truncate(name, 90)}
              />
            )}
            {loading ? (
              <LoadingIndicator />
            ) : noReports ? (
              <p className="muted">No reports created yet</p>
            ) : (
              ''
            )}
          </ControlGroup>
          <p className="ReportModal__externalSourceLink" onClick={this.toggleExternal}>
            {external ? '- Add Optimize Report' : '+ Add External Source'}
          </p>
          {external && (
            <ControlGroup className="externalSourceGroup">
              <Labeled label="Enter URL of external datasource to be included on the dashboard">
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
              </Labeled>
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
          Select...
        </Select.Option>
      );
    } else {
      return '';
    }
  };
}
