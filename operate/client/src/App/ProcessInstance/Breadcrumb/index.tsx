/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {tracking} from 'modules/tracking';
import {CarbonBreadcrumb} from './styled';
import {Link} from 'modules/components/Link';
import {OverflowMenu, OverflowMenuItem, BreadcrumbItem} from '@carbon/react';
import {useNavigate} from 'react-router-dom';
import {Paths} from 'modules/Routes';

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
      callHierarchy.length - 1,
    );
  }

  return (
    <CarbonBreadcrumb noTrailingSlash>
      {breadcrumbs.map(({instanceId, processDefinitionName}) => {
        return (
          <BreadcrumbItem key={instanceId}>
            <Link
              to={Paths.processInstance(instanceId)}
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
                      navigate(Paths.processInstance(instanceId));
                    }}
                  />
                ),
              )}
            </OverflowMenu>
          </BreadcrumbItem>
          {lastBreadcrumb !== undefined && (
            <BreadcrumbItem key={lastBreadcrumb.instanceId}>
              <Link
                to={Paths.processInstance(lastBreadcrumb.instanceId)}
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
