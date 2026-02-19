/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import {tracking} from 'modules/tracking';
import {InstanceHeader} from 'modules/components/InstanceHeader';
import {Skeleton} from 'modules/components/InstanceHeader/Skeleton';
import {Link} from 'modules/components/Link';
import {Locations, Paths} from 'modules/Routes';
import {formatDate} from 'modules/utils/date';
import {useAvailableTenants} from 'modules/queries/useAvailableTenants';
import {useDecisionInstance} from 'modules/queries/decisionInstances/useDecisionInstance';
import type {DrdPanelState} from 'modules/queries/decisionInstances/useDrdPanelState';
import {getClientConfig} from 'modules/utils/getClientConfig';

const getHeaderColumns = (isMultiTenancyEnabled: boolean = false) => {
  return [
    {
      name: 'Decision Name',
      skeletonWidth: '136px',
    },
    {
      name: 'Decision Instance Key',
      skeletonWidth: '137px',
    },
    {
      name: 'Version',
      skeletonWidth: '33px',
    },
    ...(isMultiTenancyEnabled
      ? [
          {
            name: 'Tenant',
            skeletonWidth: '34px',
          },
        ]
      : []),
    {
      name: 'Evaluation Date',
      skeletonWidth: '143px',
    },
    {
      name: 'Process Instance Key',
      skeletonWidth: '137px',
    },
  ];
};

type HeaderProps = {
  decisionEvaluationInstanceKey: string;
  onChangeDrdPanelState(state: DrdPanelState): void;
};

const Header: React.FC<HeaderProps> = ({
  decisionEvaluationInstanceKey,
  onChangeDrdPanelState,
}) => {
  const isMultiTenancyEnabled = getClientConfig().multiTenancyEnabled;
  const headerColumns = getHeaderColumns(isMultiTenancyEnabled);
  const tenantsById = useAvailableTenants();
  const {data: decisionInstance, status} = useDecisionInstance(
    decisionEvaluationInstanceKey,
  );

  if (status === 'pending') {
    return <Skeleton headerColumns={headerColumns} />;
  }

  if (status === 'success' && decisionInstance !== null) {
    const tenantId = decisionInstance.tenantId;
    const tenantName = tenantsById[tenantId] ?? tenantId;
    const versionColumnTitle = `View decision "${
      decisionInstance.decisionDefinitionName
    } version ${decisionInstance.decisionDefinitionVersion}" instances${
      isMultiTenancyEnabled ? ` - ${tenantName}` : ''
    }`;

    return (
      <InstanceHeader
        state={decisionInstance.state}
        headerColumns={headerColumns.map(({name}) => name)}
        bodyColumns={[
          {
            title: decisionInstance.decisionDefinitionName,
            content: decisionInstance.decisionDefinitionName,
          },
          {
            title: decisionInstance.decisionEvaluationInstanceKey,
            content: decisionInstance.decisionEvaluationInstanceKey,
          },
          {
            hideOverflowingContent: false,
            content: (
              <Link
                to={Locations.decisions({
                  version:
                    decisionInstance.decisionDefinitionVersion.toString(),
                  name: decisionInstance.decisionDefinitionId,
                  evaluated: true,
                  failed: true,
                  ...(isMultiTenancyEnabled
                    ? {
                        tenant: tenantId,
                      }
                    : {}),
                })}
                title={versionColumnTitle}
                aria-label={versionColumnTitle}
                onClick={() => {
                  tracking.track({
                    eventName: 'navigation',
                    link: 'decision-details-version',
                  });
                }}
              >
                {decisionInstance.decisionDefinitionVersion}
              </Link>
            ),
          },
          ...(isMultiTenancyEnabled
            ? [
                {
                  title: tenantName,
                  content: tenantName,
                },
              ]
            : []),
          {
            title: formatDate(decisionInstance.evaluationDate) ?? '--',
            content: formatDate(decisionInstance.evaluationDate),
          },
          {
            title: decisionInstance.processInstanceKey ?? 'None',
            hideOverflowingContent: false,
            content: (
              <>
                {decisionInstance.processInstanceKey ? (
                  <Link
                    to={Paths.processInstance(
                      decisionInstance.processInstanceKey,
                    )}
                    title={`View process instance ${decisionInstance.processInstanceKey}`}
                    aria-label={`View process instance ${decisionInstance.processInstanceKey}`}
                    onClick={() => {
                      tracking.track({
                        eventName: 'navigation',
                        link: 'decision-details-parent-process-details',
                      });
                    }}
                  >
                    {decisionInstance.processInstanceKey}
                  </Link>
                ) : (
                  'None'
                )}
              </>
            ),
          },
        ]}
        additionalContent={
          <Button
            size="sm"
            kind="tertiary"
            title="Open Decision Requirements Diagram"
            aria-label="Open Decision Requirements Diagram"
            onClick={() => {
              onChangeDrdPanelState('minimized');
              tracking.track({
                eventName: 'drd-panel-interaction',
                action: 'open',
              });
            }}
          >
            Open DRD
          </Button>
        }
      />
    );
  }
  return null;
};

export {Header};
