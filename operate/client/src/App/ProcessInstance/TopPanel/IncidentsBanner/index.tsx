/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {observer} from 'mobx-react';

type Props = {
  onClick: () => void;
  isOpen: boolean;
};

const IncidentsBanner: React.FC<Props> = observer((_props) => {
  // Replaced by CTA in header
  return null;
});

export {IncidentsBanner};
