/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Header} from './styled';
import {Title} from '../PanelTitle';

type Props = {
  title: string;
  count?: number;
  children?: React.ReactNode;
  className?: string;
  hasTopBorder?: boolean;
};

const PanelHeader: React.FC<Props> = ({
  title,
  count = 0,
  children,
  className,
}) => {
  return (
    <Header className={className}>
      <Title>
        {title}
        {count > 0 && <>&nbsp;&nbsp;&nbsp;-&nbsp;&nbsp;&nbsp;{count} results</>}
      </Title>
      {children}
    </Header>
  );
};

export {PanelHeader};
