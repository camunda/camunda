/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';
import update from 'immutability-helper';

import {Button, Icon, TextEditor} from 'components';
import {track} from 'tracking';

import TextReportEditModal from './TextReportEditModal';

import './TextReport.scss';

export default function TextReport({report, children = () => {}, onReportUpdate}) {
  const [reloadState, setReloadState] = useState(0);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const reloadReport = () => {
    setReloadState((prevReloadState) => prevReloadState + 1);
  };

  const handleEdit = () => {
    setIsModalOpen(!isModalOpen);
    track('editTextReport');
  };

  const onConfirm = (text) => {
    onReportUpdate(update(report, {configuration: {text: {$set: text}}}));
  };

  if (!report?.configuration?.text) {
    return null;
  }

  return (
    <>
      <div className="TextReport DashboardReport__wrapper">
        <TextEditor key={reloadState} initialValue={report.configuration.text} />
        {children({loadReportData: reloadReport})}
        {children && (
          <Button className="EditButton EditTextReport" onClick={handleEdit}>
            <Icon type="edit-small" />
          </Button>
        )}
      </div>
      {isModalOpen && (
        <TextReportEditModal
          initialValue={report.configuration.text}
          onClose={() => setIsModalOpen(false)}
          onConfirm={onConfirm}
        />
      )}
    </>
  );
}

TextReport.isTextReport = function (report) {
  return !!report.configuration?.text;
};
