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
import {
  type CallHierarchy,
  type ProcessInstance,
} from '@vzeta/camunda-api-zod-schemas/8.8';

type Props = {
  callHierarchy: CallHierarchy[];
  processInstance: ProcessInstance;
};

const PRECEDING_BREADCRUMB_COUNT = 2;
const MAX_BREADCRUMBS_VISIBLE = 4;

const Breadcrumb: React.FC<Props> = ({callHierarchy, processInstance}) => {
  const navigate = useNavigate();
  const {processInstanceKey, processDefinitionName} = processInstance;

  let breadcrumbs = [...callHierarchy];
  let overflowingBreadcrumbs: CallHierarchy[] = [];
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
      {breadcrumbs.map(({processInstanceKey, processDefinitionName}) => {
        return (
          <BreadcrumbItem key={processInstanceKey}>
            <Link
              to={Paths.processInstance(processInstanceKey)}
              title={`View Process ${processDefinitionName} - Instance ${processInstanceKey}`}
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
                ({processInstanceKey, processDefinitionName}) => (
                  <OverflowMenuItem
                    key={processInstanceKey}
                    itemText={processDefinitionName}
                    requireTitle
                    title={`View Process ${processDefinitionName} - Instance ${processInstanceKey}`}
                    onClick={() => {
                      navigate(Paths.processInstance(processInstanceKey));
                    }}
                  />
                ),
              )}
            </OverflowMenu>
          </BreadcrumbItem>
          {lastBreadcrumb !== undefined && (
            <BreadcrumbItem key={lastBreadcrumb.processInstanceKey}>
              <Link
                to={Paths.processInstance(lastBreadcrumb.processInstanceKey)}
                title={`View Process ${lastBreadcrumb.processDefinitionName} - Instance ${lastBreadcrumb.processInstanceKey}`}
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
        title={`Process ${processDefinitionName} - Instance ${processInstanceKey}`}
      >
        {processDefinitionName}
      </BreadcrumbItem>
    </CarbonBreadcrumb>
  );
};

export {Breadcrumb};
