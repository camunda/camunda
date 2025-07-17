/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {drdStore} from 'modules/stores/drd';
import {Button} from '@carbon/react';
import {tracking} from 'modules/tracking';
import {InstanceHeader} from 'modules/components/InstanceHeader';
import {Skeleton} from 'modules/components/InstanceHeader/Skeleton';
import {useParams} from 'react-router-dom';
import {Link} from 'modules/components/Link';
import {Locations, Paths} from 'modules/Routes';
import {formatDate} from 'modules/utils/date';
import {useCurrentUser} from 'modules/queries/useCurrentUser';

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

const Header: React.FC = observer(() => {
  const {
    state: {status, decisionInstance},
  } = decisionInstanceDetailsStore;
  const {decisionInstanceId} = useParams<{decisionInstanceId: string}>();
  const {data: currentUser} = useCurrentUser();
  const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;
  const headerColumns = getHeaderColumns(isMultiTenancyEnabled);

  if (status === 'initial') {
    return <Skeleton headerColumns={headerColumns} />;
  }

  if (status === 'fetched' && decisionInstance !== null) {
    const tenantId = decisionInstance.tenantId;
    const tenantsById: Record<string, string> =
      currentUser?.tenants.reduce(
        (acc, tenant) => ({
          [tenant.tenantId]: tenant.name,
          ...acc,
        }),
        {},
      ) ?? {};
    const tenantName = tenantsById[tenantId] ?? tenantId;
    const versionColumnTitle = `View decision "${
      decisionInstance.decisionName
    } version ${decisionInstance.decisionVersion}" instances${
      isMultiTenancyEnabled ? ` - ${tenantName}` : ''
    }`;

    return (
      <InstanceHeader
        state={decisionInstance.state}
        headerColumns={headerColumns.map(({name}) => name)}
        bodyColumns={[
          {
            title: decisionInstance.decisionName,
            content: decisionInstance.decisionName,
          },
          {
            title: decisionInstanceId,
            content: decisionInstanceId,
          },
          {
            hideOverflowingContent: false,
            content: (
              <Link
                to={Locations.decisions({
                  version: decisionInstance.decisionVersion.toString(),
                  name: decisionInstance.decisionId,
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
                {decisionInstance.decisionVersion}
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
            title: decisionInstance.processInstanceId ?? 'None',
            hideOverflowingContent: false,
            content: (
              <>
                {decisionInstance.processInstanceId ? (
                  <Link
                    to={Paths.processInstance(
                      decisionInstance.processInstanceId,
                    )}
                    title={`View process instance ${decisionInstance.processInstanceId}`}
                    aria-label={`View process instance ${decisionInstance.processInstanceId}`}
                    onClick={() => {
                      tracking.track({
                        eventName: 'navigation',
                        link: 'decision-details-parent-process-details',
                      });
                    }}
                  >
                    {decisionInstance.processInstanceId}
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
              drdStore.setPanelState('minimized');
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
});

export {Header};
