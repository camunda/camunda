/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Table, TR, TH, TD} from 'modules/components/VariablesTable';

type Props = {
  structure: ReadonlyArray<{
    header: string;
    component: React.ReactElement;
    columnWidth: string;
  }>;
  'data-testid'?: string;
};

const Skeleton: React.FC<Props> = ({structure, ...props}) => {
  return (
    <Table hideOverflow={true}>
      <thead data-testid={props['data-testid']}>
        <TR>
          {structure.map(({header, columnWidth}, index) => (
            <TH key={index} $width={columnWidth}>
              {header}
            </TH>
          ))}
        </TR>
      </thead>
      <tbody>
        {[...new Array(50)].map((_, index) => (
          <TR key={index}>
            {structure.map(({component}, index) => (
              <TD key={index}>{component}</TD>
            ))}
          </TR>
        ))}
      </tbody>
    </Table>
  );
};

export {Skeleton};
