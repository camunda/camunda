/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
