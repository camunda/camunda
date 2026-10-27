/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Link} from 'react-router-dom';

type Props = {
  itemKey: string;
  fallbackText: string;
  to?: string;
  label?: string;
};

const ItemKeyCell: React.FC<Props> = ({itemKey, fallbackText, to, label}) => {
  if (itemKey === '-1') {
    return <>{fallbackText}</>;
  }

  if (to !== undefined) {
    return (
      <Link to={to} title={label} aria-label={label}>
        {itemKey}
      </Link>
    );
  }

  return <>{itemKey}</>;
};

export {ItemKeyCell};
