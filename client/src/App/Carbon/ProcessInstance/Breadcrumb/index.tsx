/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {tracking} from 'modules/tracking';
import {CarbonBreadcrumb} from './styled';
import {Link} from 'modules/components/Carbon/Link';
import {OverflowMenu, OverflowMenuItem, BreadcrumbItem} from '@carbon/react';
import {useNavigate} from 'react-router-dom';
import {CarbonPaths} from 'modules/carbonRoutes';

type Props = {
  processInstance: ProcessInstanceEntity;
};

const PRECEDING_BREADCRUMB_COUNT = 2;
const MAX_BREADCRUMBS_VISIBLE = 4;

const Breadcrumb: React.FC<Props> = ({processInstance}) => {
  const navigate = useNavigate();

  const {id, processName, callHierarchy} = processInstance;

  let breadcrumbs: ProcessInstanceEntity['callHierarchy'] = [...callHierarchy];
  let overflowingBreadcrumbs: ProcessInstanceEntity['callHierarchy'] = [];
  const lastBreadcrumb = callHierarchy[callHierarchy.length - 1];

  if (callHierarchy.length > MAX_BREADCRUMBS_VISIBLE) {
    breadcrumbs = callHierarchy.slice(0, PRECEDING_BREADCRUMB_COUNT);
    overflowingBreadcrumbs = callHierarchy.slice(
      PRECEDING_BREADCRUMB_COUNT,
      callHierarchy.length - 1
    );
  }

  return (
    <CarbonBreadcrumb noTrailingSlash>
      {breadcrumbs.map(({instanceId, processDefinitionName}) => {
        return (
          <BreadcrumbItem key={instanceId}>
            <Link
              to={CarbonPaths.processInstance(instanceId)}
              title={`View Process ${processDefinitionName} - Instance ${instanceId}`}
              onClick={() => {
                tracking.track({
                  eventName: 'navigation',
                  link: 'process-details-breadcrumb',
                });
              }}
            >
              {`${processDefinitionName}`}
            </Link>
          </BreadcrumbItem>
        );
      })}
      {overflowingBreadcrumbs.length > 0 && (
        <>
          <BreadcrumbItem data-floating-menu-container>
            <OverflowMenu align="bottom" iconDescription="More">
              {overflowingBreadcrumbs.map(
                ({instanceId, processDefinitionName}) => (
                  <OverflowMenuItem
                    key={instanceId}
                    itemText={processDefinitionName}
                    requireTitle
                    title={`View Process ${processDefinitionName} - Instance ${instanceId}`}
                    onClick={() => {
                      navigate(CarbonPaths.processInstance(instanceId));
                    }}
                  />
                )
              )}
            </OverflowMenu>
          </BreadcrumbItem>
          {lastBreadcrumb !== undefined && (
            <BreadcrumbItem key={lastBreadcrumb.instanceId}>
              <Link
                to={CarbonPaths.processInstance(lastBreadcrumb.instanceId)}
                title={`View Process ${lastBreadcrumb.processDefinitionName} - Instance ${lastBreadcrumb.instanceId}`}
                onClick={() => {
                  tracking.track({
                    eventName: 'navigation',
                    link: 'process-details-breadcrumb',
                  });
                }}
              >
                {`${lastBreadcrumb.processDefinitionName}`}
              </Link>
            </BreadcrumbItem>
          )}
        </>
      )}
      <BreadcrumbItem
        isCurrentPage
        title={`Process ${processName} - Instance ${id}`}
      >
        {processName}
      </BreadcrumbItem>
    </CarbonBreadcrumb>
  );
};

export {Breadcrumb};
