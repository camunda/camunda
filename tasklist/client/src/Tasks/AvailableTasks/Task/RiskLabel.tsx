/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styles from './styles.module.scss';
import {Popover} from '@carbon/react';
import {WarningHexFilled} from '@carbon/icons-react';
import {LabelWithPopover} from './LabelWithPopover';
import {getRiskLabel} from 'modules/utils/getRiskLabel';

import type {Task as TaskType} from 'modules/types';

type PriorityLabelProps = {
  risk: TaskType['risk'];
  align?: React.ComponentProps<typeof Popover>['align'];
};

const RiskLabel: React.FC<PriorityLabelProps> = ({risk, align = 'top-end'}) => {
  const riskLabel = getRiskLabel(risk);

  return (
    <LabelWithPopover
      title={riskLabel.long}
      popoverContent={
        <span className={styles.popoverBody}>{riskLabel.long}</span>
      }
      align={align}
    >
      <WarningHexFilled
        className={`
      ${styles['inlineIcon']} ${styles[`risk-${risk}`]}
      `}
      />
      {riskLabel.short}
    </LabelWithPopover>
  );
};

export {RiskLabel};
