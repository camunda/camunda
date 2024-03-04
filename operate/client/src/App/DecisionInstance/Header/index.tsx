/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import {authenticationStore} from 'modules/stores/authentication';

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

  const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;
  const headerColumns = getHeaderColumns(isMultiTenancyEnabled);

  if (status === 'initial') {
    return <Skeleton headerColumns={headerColumns} />;
  }

  if (status === 'fetched' && decisionInstance !== null) {
    const tenantId = decisionInstance.tenantId;
    const tenantName = authenticationStore.tenantsById?.[tenantId] ?? tenantId;
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
