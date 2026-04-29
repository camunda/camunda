/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation} from '@tanstack/react-query';
import type {QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {
  exportProcessInstancesCsv,
  type ExportResult,
} from 'modules/api/v2/processInstances/exportProcessInstances';
import {notificationsStore} from 'modules/stores/notifications';
import {handleOperationError} from 'modules/utils/notifications';
import {tracking} from 'modules/tracking';
import type {RequestError} from 'modules/request';

const triggerBrowserDownload = (blob: Blob) => {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `process-instances-${formatStamp(new Date())}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  // Defer revocation so Safari/Firefox have time to actually start the download
  // before the blob URL is invalidated. Synchronous revoke is observed to cancel
  // the download in some browsers.
  setTimeout(() => URL.revokeObjectURL(url), 1_000);
};

const formatStamp = (date: Date) =>
  date.toISOString().replace(/[:.]/g, '-').replace(/-\d{3}Z$/, 'Z');

const useExportProcessInstances = () => {
  return useMutation<ExportResult, RequestError, QueryProcessInstancesRequestBody>({
    mutationKey: ['exportProcessInstancesCsv'],
    mutationFn: async (payload) => {
      const {response, error} = await exportProcessInstancesCsv(payload);
      if (response !== null) {
        return response;
      }
      throw error;
    },
    onSuccess: (result) => {
      triggerBrowserDownload(result.blob);
      if (result.truncated) {
        notificationsStore.displayNotification({
          kind: 'info',
          title: 'Export was truncated',
          subtitle:
            'Only the first batch of matching process instances was exported. Refine your filter to export the full set.',
          isDismissable: true,
        });
      }
      tracking.track({
        eventName: 'process-instances-export',
        format: 'csv',
        truncated: result.truncated,
      });
    },
    onError: (error) => {
      handleOperationError(
        error.variant === 'failed-response' ? error.response.status : 0,
      );
    },
  });
};

export {useExportProcessInstances};
