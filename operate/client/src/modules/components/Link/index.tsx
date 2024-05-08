/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Link as BaseLink} from 'react-router-dom';

type Props = {
  children: React.ReactNode;
  className?: string;
} & React.ComponentProps<typeof BaseLink>;

const Link: React.FC<Props> = ({children, className, ...props}) => {
  return (
    <BaseLink className={`cds--link ${className ?? ''}`} {...props}>
      {children}
    </BaseLink>
  );
};

export {Link};
