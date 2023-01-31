/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {SkeletonText} from 'modules/components/SkeletonText';
import {LeftTD, RightTD, Table, TR} from 'modules/components/Table';
import {VariblesSkeleton} from 'modules/components/VariablesSkeleton';

const Skeleton: React.FC = () => {
  return (
    <>
      <Table data-testid="details-skeleton">
        <tbody>
          <TR>
            <LeftTD>
              <SkeletonText width="70px" />
            </LeftTD>
            <RightTD>
              <SkeletonText width="115px" />
            </RightTD>
          </TR>
          <TR>
            <LeftTD>
              <SkeletonText width="92px" />
            </LeftTD>
            <RightTD>
              <SkeletonText width="95px" />
            </RightTD>
          </TR>
          <TR>
            <LeftTD>
              <SkeletonText width="88px" />
            </LeftTD>
            <RightTD>
              <SkeletonText width="144px" />
            </RightTD>
          </TR>
          <TR>
            <LeftTD>
              <SkeletonText width="88px" />
            </LeftTD>
            <RightTD>
              <SkeletonText width="286px" />
            </RightTD>
          </TR>
        </tbody>
      </Table>
      <VariblesSkeleton />
    </>
  );
};

export {Skeleton};
