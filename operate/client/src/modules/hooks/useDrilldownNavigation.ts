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
import {searchElementInstances} from 'modules/api/v2/elementInstances/searchElementInstances';
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

  // Resolves the elementId (a static BPMN node) to the runtime element
  // instance it produced in this process instance. Returns null when the
  // element has no instance yet, or more than one (e.g. a multi-instance
  // call activity) — both cases are treated as unresolvable rather than
  // guessed at.
  async function resolveElementInstanceKey(elementId: string) {
    const response = await queryClient.fetchQuery({
      queryKey: queryKeys.elementInstances.search({
        elementId,
        processInstanceKey,
      }),
      queryFn: async () => {
        const {response, error} = await searchElementInstances({
          filter: {elementId, processInstanceKey},
          page: {limit: 1},
        });
        if (response !== null) {
          return response;
        }
        throw error;
      },
    });

    return response.page.totalItems === 1
      ? response.items[0]!.elementInstanceKey
      : null;
  }

  function drillDownToCalledProcess(elementId: string) {
    startTransition(async () => {
      setPendingDrillDownElementId(elementId);

      try {
        const elementInstanceKey = await resolveElementInstanceKey(elementId);
        if (elementInstanceKey === null) {
          return;
        }

        const response = await queryClient.fetchQuery({
          queryKey: queryKeys.processInstances.search({
            filter: {parentElementInstanceKey: elementInstanceKey},
            page: {limit: 1},
          }),
          queryFn: async () => {
            const {response, error} = await searchProcessInstances({
              filter: {parentElementInstanceKey: elementInstanceKey},
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
            Paths.processInstanceDetails({
              processInstanceId: response.items[0]!.processInstanceKey,
            }),
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
        const elementInstanceKey = await resolveElementInstanceKey(elementId);
        if (elementInstanceKey === null) {
          return;
        }

        const response = await queryClient.fetchQuery({
          queryKey: queryKeys.decisionInstances.search({
            filter: {elementInstanceKey},
            page: {limit: 1},
          }),
          queryFn: async () => {
            const {response, error} = await searchDecisionInstances({
              filter: {elementInstanceKey},
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
