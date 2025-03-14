/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLocation} from 'react-router-dom';

const LocationLog: React.FC = () => {
  const location = useLocation();

  return (
    <>
      <div data-testid="pathname">{location.pathname}</div>
      <div data-testid="search">{location.search}</div>
    </>
  );
};

export {LocationLog};
