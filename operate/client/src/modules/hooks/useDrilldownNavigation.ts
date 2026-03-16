/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useTransition} from 'react';
import {useNavigate} from 'react-router-dom';
import {useQueryClient} from '@tanstack/react-query';
import {searchProcessInstances} from 'modules/api/v2/processInstances/searchProcessInstances';
import {searchDecisionInstances} from 'modules/api/v2/decisionInstances/searchDecisionInstances';
import {Paths} from 'modules/Routes';
import {notificationsStore} from 'modules/stores/notifications';
import {queryKeys} from 'modules/queries/queryKeys';
import type {ElementType} from 'bpmn-js/lib/NavigatedViewer';

const useDrillDownNavigation = (processInstanceKey: string) => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [isPending, startTransition] = useTransition();
  const [pendingDrillDownElementId, setPendingDrillDownElementId] = useState<
    string | null
  >(null);

  function handleDrillDown(elementId: string, elementType: ElementType) {
    if (isPending) {
      return;
    }

    if (elementType === 'bpmn:CallActivity') {
      drillDownToCalledProcess(elementId);
    } else if (elementType === 'bpmn:BusinessRuleTask') {
      drillDownToCalledDecision(elementId);
    }
  }

  function drillDownToCalledProcess(elementId: string) {
    startTransition(async () => {
      setPendingDrillDownElementId(elementId);

      try {
        const response = await queryClient.fetchQuery({
          queryKey: queryKeys.processInstances.search({
            filter: {parentProcessInstanceKey: processInstanceKey},
            page: {limit: 1},
          }),
          queryFn: async () => {
            const {response, error} = await searchProcessInstances({
              filter: {parentProcessInstanceKey: processInstanceKey},
              page: {limit: 1},
            });
            if (response !== null) {
              return response;
            }
            throw error;
          },
        });

        if (response.page.totalItems === 1) {
          navigate(
            Paths.processInstance(response.items[0]!.processInstanceKey),
          );
        }
      } catch {
        notificationsStore.displayNotification({
          kind: 'error',
          title: 'Failed to resolve called instances',
          isDismissable: true,
        });
      } finally {
        setPendingDrillDownElementId(null);
      }
    });
  }

  function drillDownToCalledDecision(elementId: string) {
    startTransition(async () => {
      setPendingDrillDownElementId(elementId);

      try {
        const response = await queryClient.fetchQuery({
          queryKey: queryKeys.decisionInstances.search({
            filter: {processInstanceKey},
            page: {limit: 1},
          }),
          queryFn: async () => {
            const {response, error} = await searchDecisionInstances({
              filter: {processInstanceKey},
              page: {limit: 1},
            });
            if (response !== null) {
              return response;
            }
            throw error;
          },
        });

        if (response.page.totalItems === 1) {
          navigate(
            Paths.decisionInstance(
              response.items[0]!.decisionEvaluationInstanceKey,
            ),
          );
        }
      } catch {
        notificationsStore.displayNotification({
          kind: 'error',
          title: 'Failed to resolve called decision instances',
          isDismissable: true,
        });
      } finally {
        setPendingDrillDownElementId(null);
      }
    });
  }

  return {handleDrillDown, pendingDrillDownElementId};
};

export {useDrillDownNavigation};
