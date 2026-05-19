/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId} from 'react';
import {MetricCardContainer, MetricCardTitle, MetricCardValue} from './styled';

type MetricCardProps = {
  title: string;
  value: number | string;
  children?: React.ReactNode;
};

const MetricCard: React.FC<MetricCardProps> = ({title, value, children}) => {
  const id = useId();
  return (
    <MetricCardContainer aria-labelledby={id}>
      <MetricCardTitle id={id}>{title}</MetricCardTitle>
      <MetricCardValue>{value}</MetricCardValue>
      {children}
    </MetricCardContainer>
  );
};

export {MetricCard};
