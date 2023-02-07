/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';

import {Button, Icon, TextEditor} from 'components';
import {addNotification} from 'notifications';
import {t} from 'translation';
import {track} from 'tracking';

import './TextReport.scss';

export default function TextReport({report, children = () => {}}) {
  const [reloadState, setReloadState] = useState(0);

  const reloadReport = () => {
    setReloadState((prevReloadState) => prevReloadState + 1);
  };

  const handleEdit = () => {
    addNotification(t('dashboard.textReportEditNotification'));
    track('editTextReport');
  };

  if (!report?.configuration?.text) {
    return null;
  }

  return (
    <div className="TextReport DashboardReport__wrapper">
      <TextEditor key={reloadState} initialValue={report.configuration.text} />
      {children({loadReportData: reloadReport})}
      {children && (
        <Button className="EditButton EditTextReport" onClick={handleEdit}>
          <Icon type="edit-small" />
        </Button>
      )}
    </div>
  );
}

TextReport.isTextReport = function (report) {
  return !!report.configuration?.text;
};
