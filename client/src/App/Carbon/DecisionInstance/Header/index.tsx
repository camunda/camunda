/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {drdStore} from 'modules/stores/drd';
import {Button} from '@carbon/react';
import {tracking} from 'modules/tracking';
import {InstanceHeader} from 'modules/components/Carbon/InstanceHeader';
import {Skeleton} from 'modules/components/Carbon/InstanceHeader/Skeleton';
import {useParams} from 'react-router-dom';
import {Link} from 'modules/components/Carbon/Link';
import {Locations, Paths} from 'modules/Routes';
import {formatDate} from 'modules/utils/date';

const headerColumns = [
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
  {
    name: 'Evaluation Date',
    skeletonWidth: '143px',
  },
  {
    name: 'Process Instance Key',
    skeletonWidth: '137px',
  },
];

const Header: React.FC = observer(() => {
  const {
    state: {status, decisionInstance},
  } = decisionInstanceDetailsStore;
  const {decisionInstanceId} = useParams<{decisionInstanceId: string}>();

  if (status === 'initial') {
    return <Skeleton headerColumns={headerColumns} />;
  }

  if (status === 'fetched' && decisionInstance !== null) {
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
            content: (
              <Link
                to={Locations.decisions({
                  version: decisionInstance.decisionVersion.toString(),
                  name: decisionInstance.decisionId,
                  evaluated: true,
                  failed: true,
                })}
                title={`View decision ${decisionInstance.decisionName} version ${decisionInstance.decisionVersion} instances`}
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
          {
            title: formatDate(decisionInstance.evaluationDate) ?? '--',
            content: formatDate(decisionInstance.evaluationDate),
          },
          {
            title: decisionInstance.processInstanceId ?? 'None',
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
