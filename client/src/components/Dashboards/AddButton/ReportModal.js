/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {
  Modal,
  Button,
  ButtonGroup,
  Input,
  Typeahead,
  LoadingIndicator,
  Labeled,
  Form
} from 'components';

import {loadEntities} from 'services';
import {t} from 'translation';

export default class ReportModal extends React.Component {
  state = {
    availableReports: null,
    selectedReportId: '',
    external: false,
    externalUrl: ''
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

  setExternal = external => this.setState({external});

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

    const selectedReport = !loading && availableReports.find(({id}) => selectedReportId === id);

    return (
      <Modal
        className="ReportModal"
        open
        onClose={this.props.close}
        onConfirm={!isInvalid ? this.addReport : undefined}
      >
        <Modal.Header>{t('dashboard.addButton.addReport')}</Modal.Header>
        <Modal.Content>
          <Form>
            <ButtonGroup>
              <Button onClick={() => this.setExternal(false)} active={!external}>
                {t('dashboard.addButton.selectReport')}
              </Button>
              <Button onClick={() => this.setExternal(true)} active={external}>
                {t('dashboard.addButton.addExternal')}
              </Button>
            </ButtonGroup>
            {!external && (
              <Form.Group>
                {!loading && (
                  <Labeled label={t('dashboard.addButton.addReportLabel')}>
                    <Typeahead
                      initialValue={selectedReport}
                      disabled={noReports}
                      placeholder={t('dashboard.addButton.selectReportPlaceholder')}
                      values={availableReports}
                      onSelect={this.selectReport}
                      formatter={({name}) => this.truncate(name, 74)}
                      noValuesMessage={t('dashboard.addButton.noReports')}
                    />
                  </Labeled>
                )}
                {loading && <LoadingIndicator />}
              </Form.Group>
            )}
            {external && (
              <Form.Group>
                <Labeled label={t('dashboard.addButton.externalUrl')}>
                  <Input
                    name="externalInput"
                    className="externalInput"
                    placeholder="https://www.example.com/widget/embed.html"
                    value={externalUrl}
                    onChange={({target: {value}}) =>
                      this.setState({
                        externalUrl: value
                      })
                    }
                  />
                </Labeled>
              </Form.Group>
            )}
          </Form>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.close}>{t('common.cancel')}</Button>
          <Button variant="primary" color="blue" onClick={this.addReport} disabled={isInvalid}>
            {t('dashboard.addButton.addReportLabel')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }

  truncate = (str, index) => {
    return str.length > index ? str.substr(0, index - 1) + '\u2026' : str;
  };
}
