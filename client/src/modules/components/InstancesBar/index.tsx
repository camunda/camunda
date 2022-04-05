/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import * as Styled from './styled';

type Props = {
  label?: string;
  activeCount?: number;
  incidentsCount: number;
  className?: string;
  size: 'small' | 'medium' | 'large';
  barHeight: number;
};

function InstancesBar(props: Props) {
  const {label, activeCount, incidentsCount, size, barHeight} = props;
  const incidentsBarRatio =
    // @ts-expect-error ts-migrate(2532) FIXME: Object is possibly 'undefined'.
    (100 * incidentsCount) / (activeCount + incidentsCount);

  const hasIncidents = incidentsCount > 0;
  // @ts-expect-error ts-migrate(2532) FIXME: Object is possibly 'undefined'.
  const hasActive = activeCount > 0;

  return (
    <div className={props.className}>
      <Styled.Wrapper size={size}>
        <Styled.IncidentsCount
          data-testid="incident-instances-badge"
          hasIncidents={hasIncidents}
        >
          {incidentsCount}
        </Styled.IncidentsCount>
        <Styled.Label data-testid="incident-message">{label}</Styled.Label>
        {/* @ts-expect-error ts-migrate(2532) FIXME: Object is possibly 'undefined'. */}
        {activeCount >= 0 && (
          <Styled.ActiveCount
            data-testid="active-instances-badge"
            hasActive={hasActive}
          >
            {activeCount}
          </Styled.ActiveCount>
        )}
      </Styled.Wrapper>
      <Styled.BarContainer height={barHeight}>
        <Styled.Bar hasActive={hasActive} />
        <Styled.IncidentsBar
          data-testid="incidents-bar"
          style={{
            width: `${incidentsBarRatio}%`,
          }}
        />
      </Styled.BarContainer>
    </div>
  );
}

export {Wrapper, Bar} from './styled';
export default InstancesBar;
