/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {RouteComponentProps, withRouter} from 'react-router-dom';
import {Button} from '@carbon/react';
import {SerializedEditorState} from 'lexical';

import {
  Modal,
  Input,
  Typeahead,
  LoadingIndicator,
  Labeled,
  Form,
  Tabs,
  Icon,
  TextEditor,
} from 'components';
import {getCollection, isTextTileValid, loadReports} from 'services';
import {t} from 'translation';
import {DashboardTile, GenericReport} from 'types';

interface CreateTileModalProps extends RouteComponentProps {
  close: () => void;
  confirm: (tileConfig: Partial<DashboardTile>) => void;
}

type TabOpen = 'optimize_report' | 'external_url' | 'text';

export function CreateTileModal({close, confirm, location}: CreateTileModalProps) {
  const [availableReports, setAvailableReports] = useState<GenericReport[] | null>(null);
  const [selectedReportId, setSelectedReportId] = useState<string>('');
  const [externalUrl, setExternalUrl] = useState<string>('');
  const [tabOpen, setTabOpen] = useState<TabOpen>('optimize_report');
  const [text, setText] = useState<SerializedEditorState>();

  useEffect(() => {
    (async () => {
      const collection = getCollection(location.pathname);
      const availableReports = await loadReports(collection);
      setAvailableReports(availableReports);
    })();
  }, [location.pathname]);

  const addTile = () => {
    confirm(getTileConfig(tabOpen, externalUrl, text, selectedReportId));
  };

  const getTileConfig = (
    tabOpen: TabOpen,
    externalUrl: string,
    text: SerializedEditorState | undefined,
    selectedReportId: string
  ): Partial<DashboardTile> => {
    if (tabOpen === 'external_url') {
      return {id: '', configuration: {external: externalUrl}, type: 'external_url'};
    } else if (tabOpen === 'text') {
      return {id: '', configuration: {text}, type: 'text'};
    }
    return {id: selectedReportId, type: 'optimize_report'};
  };

  const isExternalUrlValid = (url: string) => {
    // url has to start with https:// or http://
    return url.match(/^(https|http):\/\/.+/);
  };

  const textLength = TextEditor.getEditorStateLength(text);

  const isCurrentTabInvalid = (tabOpen: TabOpen, externalUrl: string, selectedReportId: string) => {
    const isInvalidMap: Record<TabOpen, boolean> = {
      optimize_report: !selectedReportId,
      external_url: !isExternalUrlValid(externalUrl),
      text: !isTextTileValid(textLength),
    };

    return isInvalidMap[tabOpen];
  };

  const isInvalid = isCurrentTabInvalid(tabOpen, externalUrl, selectedReportId);
  const loading = availableReports === null;
  const selectedReport = (!loading && availableReports.find(({id}) => selectedReportId === id)) || {
    id: undefined,
  };

  return (
    <Modal className="CreateTileModal" open onClose={close} isOverflowVisible>
      <Modal.Header>{t('dashboard.addButton.addTile')}</Modal.Header>
      <Modal.Content>
        <Form>
          <Tabs<TabOpen> value={tabOpen} onChange={setTabOpen}>
            <Tabs.Tab value="optimize_report" title={t('dashboard.addButton.optimizeReport')}>
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
            <Tabs.Tab value="external_url" title={t('dashboard.addButton.externalWebsite')}>
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
      <Modal.Footer>
        <Button kind="secondary" onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button onClick={addTile} disabled={isInvalid}>
          {t('dashboard.addButton.addTileLabel')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default withRouter(CreateTileModal);
