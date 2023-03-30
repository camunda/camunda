/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';

import {Button, Icon} from 'components';
import {t} from 'translation';

import ReportModal from './ReportModal';
import ReportCreationModal from './ReportCreationModal';

const size = {width: 6, height: 4};

export default function AddButton({addReport, existingReport}) {
  const [open, setOpen] = useState(false);
  const [creatingNewReport, setCreatingNewReport] = useState(false);

  const closeModal = (evt) => {
    if (evt) {
      evt.stopPropagation();
    }
    setOpen(false);
  };

  const onConfirm = async (props) => {
    closeModal();
    setCreatingNewReport(false);

    if (props.id === 'newReport') {
      return setCreatingNewReport(true);
    }

    // position does not matter because the report will be positioned by the user
    const payload = {
      configuration: null,
      position: {x: 0, y: 0},
      dimensions: size,
      ...props,
    };

    addReport(payload);
  };

  return (
    <>
      <Button main className="AddButton tool-button" onClick={() => setOpen(true)}>
        <Icon type="plus" /> {t('dashboard.addButton.addTile')}
        {open && <ReportModal close={closeModal} confirm={onConfirm} />}
      </Button>
      {creatingNewReport && (
        <ReportCreationModal
          onClose={() => setCreatingNewReport(false)}
          existingReport={existingReport}
          onConfirm={onConfirm}
        />
      )}
    </>
  );
}
