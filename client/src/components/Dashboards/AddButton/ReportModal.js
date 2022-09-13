/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {withRouter} from 'react-router-dom';

import {
  Modal,
  Button,
  Input,
  Typeahead,
  LoadingIndicator,
  Labeled,
  Form,
  Tabs,
  Icon,
} from 'components';
import {getCollection, loadReports} from 'services';
import {t} from 'translation';

export default withRouter(
  class ReportModal extends React.Component {
    state = {
      availableReports: null,
      selectedReportId: '',
      external: false,
      externalUrl: '',
    };

    componentDidMount = async () => {
      const collection = getCollection(this.props.location.pathname);

      this.setState({
        availableReports: await loadReports(collection),
      });
    };

    selectReport = (id) => {
      this.setState({
        selectedReportId: id,
      });
    };

    addReport = () => {
      const {external, selectedReportId, externalUrl} = this.state;
      this.props.confirm(
        external ? {id: '', configuration: {external: externalUrl}} : {id: selectedReportId}
      );
    };

    setExternal = (external) => this.setState({external});

    isValid = (url) => {
      // url has to start with https:// or http://
      return url.match(/^(https|http):\/\/.+/);
    };

    render() {
      const loading = this.state.availableReports === null;

      const {external, externalUrl, selectedReportId, availableReports} = this.state;
      const isInvalidExternal = !this.isValid(externalUrl);

      const isInvalid = external ? isInvalidExternal : !selectedReportId;

      const selectedReport =
        (!loading && availableReports.find(({id}) => selectedReportId === id)) || {};

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
              <Tabs value={external} onChange={this.setExternal}>
                <Tabs.Tab value={false} title={t('dashboard.addButton.optimizeReport')}>
                  <Form.Group>
                    {!loading && (
                      <Labeled label={t('dashboard.addButton.addReportLabel')}>
                        <Typeahead
                          initialValue={selectedReport.id}
                          placeholder={t('dashboard.addButton.selectReportPlaceholder')}
                          onChange={this.selectReport}
                          noValuesMessage={t('dashboard.addButton.noReports')}
                        >
                          <Typeahead.Option
                            key="newReport"
                            value="newReport"
                            label={`+ ${t('dashboard.addButton.newReport')}`}
                          >
                            <Icon type="plus" />
                            <b>{t('dashboard.addButton.newReport')}</b>
                          </Typeahead.Option>
                          {availableReports.map(({id, name}) => (
                            <Typeahead.Option key={id} value={id}>
                              {name}
                            </Typeahead.Option>
                          ))}
                        </Typeahead>
                      </Labeled>
                    )}
                    {loading && <LoadingIndicator />}
                  </Form.Group>
                </Tabs.Tab>
                <Tabs.Tab value={true} title={t('dashboard.addButton.externalUrl')}>
                  <Form.Group>
                    <Labeled label={t('dashboard.addButton.externalUrl')}>
                      <Input
                        name="externalInput"
                        className="externalInput"
                        placeholder="https://www.example.com/widget/embed.html"
                        value={externalUrl}
                        onChange={({target: {value}}) =>
                          this.setState({
                            externalUrl: value,
                          })
                        }
                      />
                    </Labeled>
                  </Form.Group>
                </Tabs.Tab>
              </Tabs>
            </Form>
          </Modal.Content>
          <Modal.Actions>
            <Button main onClick={this.props.close}>
              {t('common.cancel')}
            </Button>
            <Button main primary onClick={this.addReport} disabled={isInvalid}>
              {t('dashboard.addButton.addReportLabel')}
            </Button>
          </Modal.Actions>
        </Modal>
      );
    }
  }
);
