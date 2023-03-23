/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';
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
  TextEditor,
} from 'components';
import {getCollection, isTextReportValid, loadReports} from 'services';
import {t} from 'translation';

function ReportModal({close, confirm, location}) {
  const [availableReports, setAvailableReports] = useState(null);
  const [selectedReportId, setSelectedReportId] = useState('');
  const [externalUrl, setExternalUrl] = useState('');
  const [tabOpen, setTabOpen] = useState('report');
  const [text, setText] = useState();

  useEffect(() => {
    (async () => {
      const collection = getCollection(location.pathname);
      const availableReports = await loadReports(collection);
      setAvailableReports(availableReports);
    })();
  }, [location.pathname]);

  const addReport = () => {
    confirm(getReportConfig(tabOpen, externalUrl, text, selectedReportId));
  };

  const getReportConfig = (tabOpen, externalUrl, text, selectedReportId) => {
    if (tabOpen === 'external') {
      return {id: '', configuration: {external: externalUrl}, type: 'external_url'};
    } else if (tabOpen === 'text') {
      return {id: '', configuration: {text}, type: 'text'};
    }
    return {id: selectedReportId, type: 'optimize_report'};
  };

  const isExternalUrlValid = (url) => {
    // url has to start with https:// or http://
    return url.match(/^(https|http):\/\/.+/);
  };

  const textLength = TextEditor.getEditorStateLength(text);

  const isCurrentTabInvalid = (tabOpen, externalUrl, selectedReportId, text) => {
    const isInvalidMap = {
      report: !selectedReportId,
      external: !isExternalUrlValid(externalUrl),
      text: !isTextReportValid(textLength),
    };

    return isInvalidMap[tabOpen];
  };

  const isInvalid = isCurrentTabInvalid(tabOpen, externalUrl, selectedReportId, text);
  const loading = availableReports === null;
  const selectedReport =
    (!loading && availableReports.find(({id}) => selectedReportId === id)) || {};

  return (
    <Modal
      className="ReportModal"
      open
      onClose={close}
      onConfirm={!isInvalid ? addReport : undefined}
    >
      <Modal.Header>{t('dashboard.addButton.addTile')}</Modal.Header>
      <Modal.Content>
        <Form>
          <Tabs value={tabOpen} onChange={setTabOpen}>
            <Tabs.Tab value="report" title={t('dashboard.addButton.optimizeReport')}>
              <Form.Group>
                {!loading && (
                  <Labeled label={t('dashboard.addButton.addReportLabel')}>
                    <Typeahead
                      initialValue={selectedReport.id}
                      placeholder={t('dashboard.addButton.selectReportPlaceholder')}
                      onChange={setSelectedReportId}
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
            <Tabs.Tab value="external" title={t('dashboard.addButton.externalWebsite')}>
              <Form.Group>
                <Labeled label={t('dashboard.addButton.externalWebsite')}>
                  <Input
                    name="externalInput"
                    className="externalInput"
                    placeholder="https://www.example.com/widget/embed.html"
                    value={externalUrl}
                    onChange={({target: {value}}) => setExternalUrl(value)}
                  />
                </Labeled>
              </Form.Group>
            </Tabs.Tab>
            <Tabs.Tab value="text" title={t('dashboard.addButton.text')}>
              <Form.Group className="Labeled">
                <span className="label before">{t('dashboard.addButton.text')}</span>
                <TextEditor initialValue={text} onChange={setText} />
                <TextEditor.CharCount editorState={text} />
              </Form.Group>
            </Tabs.Tab>
          </Tabs>
        </Form>
      </Modal.Content>
      <Modal.Actions>
        <Button main onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button main primary onClick={addReport} disabled={isInvalid}>
          {t('dashboard.addButton.addTileLabel')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default withRouter(ReportModal);
