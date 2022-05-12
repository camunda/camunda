/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Link} from 'modules/components/Link';
import {Paths} from 'modules/routes';
import {DecisionInstanceType} from 'modules/stores/decisionInstanceDetails';
import {tracking} from 'modules/tracking';
import {formatDate} from 'modules/utils/date/formatDate';
import {useParams} from 'react-router-dom';
import {Table, TD, TH, SkeletonBlock} from './styled';

type Props = {
  decisionInstance?: DecisionInstanceType;
  'data-testid'?: string;
};

const Details: React.FC<Props> = ({decisionInstance, ...props}) => {
  const {decisionInstanceId} = useParams<{decisionInstanceId: string}>();

  return (
    <Table data-testid={props['data-testid']}>
      <thead>
        <tr>
          <TH>Decision</TH>
          <TH>Decision Instance ID</TH>
          <TH>Version</TH>
          <TH>Evaluation Date</TH>
          <TH>Process Instance ID</TH>
        </tr>
      </thead>
      <tbody>
        {decisionInstance === undefined ? (
          <tr>
            <TD>
              <SkeletonBlock $width="200px" />
            </TD>
            <TD>
              <SkeletonBlock $width="162px" />
            </TD>
            <TD>
              <SkeletonBlock $width="17px" />
            </TD>
            <TD>
              <SkeletonBlock $width="151px" />
            </TD>
            <TD>
              <SkeletonBlock $width="162px" />
            </TD>
          </tr>
        ) : (
          <tr>
            <TD>{decisionInstance.decisionName}</TD>
            <TD>{decisionInstanceId}</TD>
            <TD>{decisionInstance.decisionVersion}</TD>
            <TD>{formatDate(decisionInstance.evaluationDate)}</TD>
            <TD>
              {decisionInstance.processInstanceId ? (
                <Link
                  to={Paths.processInstance(decisionInstance.processInstanceId)}
                  title={`View process instance ${decisionInstance.processInstanceId}`}
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
            </TD>
          </tr>
        )}
      </tbody>
    </Table>
  );
};

export {Details};
