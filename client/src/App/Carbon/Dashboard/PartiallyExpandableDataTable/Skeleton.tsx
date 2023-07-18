/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {DataTableSkeleton} from './styled';

const Skeleton = () => {
  return (
    <DataTableSkeleton
      data-testid="data-table-skeleton"
      columnCount={1}
      rowCount={20}
      showHeader={false}
      showToolbar={false}
    />
  );
};

export {Skeleton};
