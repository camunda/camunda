/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {
  Wrapper,
  IncidentsCount,
  Label,
  BarContainer,
  RightControls,
  ActiveInstancesBar,
  ActiveCount,
  IncidentsBar,
  DrainingIndicator,
} from './styled';
import {Timer} from '@carbon/react/icons';
import {Tooltip} from '@carbon/react';

type Props = {
  label?: {
    type: 'process' | 'incident';
    size: 'small' | 'medium';
    text: string;
  };
  activeInstancesCount?: number;
  incidentsCount: number;
  isDraining?: boolean;
  drainingDescription?: string;
  size: 'small' | 'medium' | 'large';
  className?: string;
};

const InstancesBar: React.FC<Props> = ({
  label,
  activeInstancesCount,
  incidentsCount,
  isDraining = false,
  drainingDescription,
  size,
  className,
}) => {
  const incidentsBarRatio =
    (100 * incidentsCount) / ((activeInstancesCount ?? 0) + incidentsCount);

  const hasIncidents = incidentsCount > 0;
  const hasActiveInstances = (activeInstancesCount ?? 0) > 0;
  const showIncidentsBar = activeInstancesCount !== undefined;
  const showActiveInstancesCount =
    activeInstancesCount !== undefined && activeInstancesCount >= 0;

  return (
    <div className={className}>
      <Wrapper $size={size}>
        <IncidentsCount
          data-testid="incident-instances-badge"
          $hasIncidents={hasIncidents}
        >
          {incidentsCount}
        </IncidentsCount>
        {label && (
          <Label
            $size={label.size}
            $isRed={label.type === 'incident' && size === 'medium'}
          >
            {label.text}
          </Label>
        )}
        {(isDraining || showActiveInstancesCount) && (
          <RightControls>
            {isDraining && (
              <Tooltip
                label={drainingDescription ?? 'Draining'}
                align="top"
                autoAlign
              >
                <DrainingIndicator data-testid="draining-indicator">
                  <Timer size={16} />
                </DrainingIndicator>
              </Tooltip>
            )}

            {showActiveInstancesCount && (
              <ActiveCount
                data-testid="active-instances-badge"
                $hasActiveInstances={hasActiveInstances}
              >
                {activeInstancesCount}
              </ActiveCount>
            )}
          </RightControls>
        )}
      </Wrapper>
      {showIncidentsBar && (
        <BarContainer>
          <ActiveInstancesBar $isPassive={!hasActiveInstances} $size={size} />
          <IncidentsBar
            $size={size}
            style={{
              width: `${incidentsBarRatio}%`,
            }}
          />
        </BarContainer>
      )}
    </div>
  );
};

export {InstancesBar};
