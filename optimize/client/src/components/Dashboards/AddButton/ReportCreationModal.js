/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {withRouter} from 'react-router';

import {t} from 'translation';
import {Loading, Modal, ReportTemplateModal} from 'components';
import {useErrorHandling} from 'hooks';
import {addNotification, showError} from 'notifications';
import {createEntity, getCollection} from 'services';

import useReportDefinitions from '../useReportDefinitions';

export function ReportCreationModal({onClose, existingReport, onConfirm, location}) {
  const {definitions} = useReportDefinitions(existingReport, showError);
  const {mightFail} = useErrorHandling();

  function createReport(report) {
    const collectionId = getCollection(location.pathname);

    mightFail(
      createEntity('report/process/single', {...report, collectionId}, 'dashboard'),
      (id) => {
        onConfirm({id, type: 'optimize_report'});
        addNotification({
          type: 'success',
          text: t('common.collection.created', {name: report.name}),
        });
      },
      showError
    );
  }

  if (!definitions) {
    return (
      <Modal open>
        <Modal.Footer>
          <Loading small />
        </Modal.Footer>
      </Modal>
    );
  }

  return (
    <ReportTemplateModal
      onClose={onClose}
      onConfirm={createReport}
      initialDefinitions={definitions}
    />
  );
}

export default withRouter(ReportCreationModal);
