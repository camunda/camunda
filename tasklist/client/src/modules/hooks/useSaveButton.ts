/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, useState} from 'react';
import {InlineLoadingStatus} from '@carbon/react';
import {Variable} from 'modules/types';
import {useSaveDraftVariables} from 'modules/mutations/useSaveDraftVariables';
import {tracking} from 'modules/tracking';

function useSaveButton(taskId: string, messageTimeoutMs = 5000) {
  const [savingState, setSavingState] =
    useState<InlineLoadingStatus>('inactive');
  const {mutateAsync: saveDraft} = useSaveDraftVariables(taskId);

  async function save(variables: Pick<Variable, 'name' | 'value'>[]) {
    setSavingState('active');
    tracking.track({eventName: 'task-manual-save'});
    try {
      await saveDraft(variables);
      setSavingState('finished');
    } catch {
      setSavingState('error');
    }
  }

  const saveTimeout = useRef<NodeJS.Timeout | null>();
  useEffect(() => {
    if (savingState === 'finished') {
      saveTimeout.current = setTimeout(() => {
        setSavingState('inactive');
      }, messageTimeoutMs);

      return () => {
        const saveTimeoutTimer = saveTimeout.current;
        if (saveTimeoutTimer) {
          clearTimeout(saveTimeoutTimer);
        }
      };
    }
    return () => {};
  }, [messageTimeoutMs, savingState]);

  return {save, savingState};
}

export {useSaveButton};
