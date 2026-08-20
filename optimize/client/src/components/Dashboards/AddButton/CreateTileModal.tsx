/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {useLocation} from 'react-router-dom';
import {Button, ComboBox, Form, TextInput, TextInputSkeleton} from '@carbon/react';
import {SerializedEditorState} from 'lexical';

import {Modal, Tabs, TextEditor} from 'components';
import {getCollection, isTextTileValid, loadReports} from 'services';
import {t} from 'translation';
import {DashboardTile, GenericReport} from 'types';

interface CreateTileModalProps {
  close: () => void;
  confirm: (tileConfig: Partial<DashboardTile>) => void;
}

type TabOpen = 'optimize_report' | 'external_url' | 'text';

export default function CreateTileModal({close, confirm}: CreateTileModalProps) {
  const [availableReports, setAvailableReports] = useState<GenericReport[] | null>(null);
  const [selectedReportId, setSelectedReportId] = useState<string>('');
  const [externalUrl, setExternalUrl] = useState<string>('');
  const [tabOpen, setTabOpen] = useState<TabOpen>('optimize_report');
  const [text, setText] = useState<SerializedEditorState | null>(null);
  const {pathname} = useLocation();

  useEffect(() => {
    (async () => {
      const collection = getCollection(pathname);
      const availableReports = await loadReports(collection);
      setAvailableReports(availableReports);
    })();
  }, [pathname]);

  const addTile = () => {
    confirm(getTileConfig(tabOpen, externalUrl, text, selectedReportId));
  };

  const getTileConfig = (
    tabOpen: TabOpen,
    externalUrl: string,
    text: SerializedEditorState | null,
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

  type ReportListItem = {id: string; name: string};
  const reportsListItems: ReportListItem[] = [
    {id: 'newReport', name: `+ ${t('dashboard.addButton.newReport')}`},
    ...(availableReports || []),
  ];
  const isInvalid = isCurrentTabInvalid(tabOpen, externalUrl, selectedReportId);
  const loading = availableReports === null;
  const selectedReport = (!loading && reportsListItems.find(({id}) => selectedReportId === id)) || {
    id: undefined,
  };

  return (
    <Modal className="CreateTileModal" open onClose={close} isOverflowVisible>
      <Modal.Header title={t('dashboard.addButton.addTile')} />
      <Modal.Content>
        <Form>
          <Tabs<TabOpen> value={tabOpen} onChange={setTabOpen}>
            <Tabs.Tab value="optimize_report" title={t('dashboard.addButton.optimizeReport')}>
              {loading ? (
                <TextInputSkeleton />
              ) : (
                <ComboBox
                  id="addReportSelector"
                  titleText={t('dashboard.addButton.addReportLabel')}
                  selectedItem={selectedReport}
                  items={reportsListItems}
                  placeholder={t('dashboard.addButton.selectReportPlaceholder').toString()}
                  onChange={({selectedItem}) => setSelectedReportId(selectedItem?.id || '')}
                  itemToString={(item) => (item as ReportListItem).name}
                />
              )}
            </Tabs.Tab>
            <Tabs.Tab value="external_url" title={t('dashboard.addButton.externalWebsite')}>
              <TextInput
                id="externalInput"
                name="externalInput"
                labelText={t('dashboard.addButton.externalWebsite')}
                className="externalInput"
                placeholder="https://www.example.com/widget/embed.html"
                value={externalUrl}
                onChange={({target: {value}}) => setExternalUrl(value)}
              />
            </Tabs.Tab>
            <Tabs.Tab value="text" title={t('dashboard.addButton.text')}>
              <TextEditor
                label={t('dashboard.addButton.text').toString()}
                initialValue={text}
                onChange={setText}
              />
              <TextEditor.CharCount editorState={text} />
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
