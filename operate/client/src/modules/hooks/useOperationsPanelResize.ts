/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import isNil from 'lodash/isNil';
import {panelStatesStore} from 'modules/stores/panelStates';

const useOperationsPanelResize = (
  targetRef: React.RefObject<HTMLElement>,
  onResize: (target: HTMLElement, width: number) => void,
) => {
  useEffect(() => {
    let observer: ResizeObserver;

    if (targetRef.current !== null) {
      observer = new ResizeObserver((entries) => {
        const [operationsPanelEntry] = entries;
        const operationsPanel = operationsPanelEntry?.target as
          | HTMLDivElement
          | undefined;

        if (
          targetRef.current !== null &&
          operationsPanel?.offsetWidth !== undefined
        ) {
          onResize(targetRef.current, operationsPanel.offsetWidth);
        }
      });

      const {operationsPanelRef} = panelStatesStore.state;

      if (!isNil(operationsPanelRef?.current)) {
        observer.observe(operationsPanelRef!.current);
      }
    }

    return () => {
      observer?.disconnect();
    };
  }, [onResize, targetRef]);
};

export {useOperationsPanelResize};
