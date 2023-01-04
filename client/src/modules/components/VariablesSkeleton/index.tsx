/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {LeftTD, RightTD, Table, TH, TR} from 'modules/components/Table';
import {TitleSkeleton} from './styled';
import {SkeletonText} from 'modules/components/SkeletonText';

const VariblesSkeleton: React.FC = () => {
  return (
    <div data-testid="variables-skeleton" key="variables-skeleton">
      <TitleSkeleton />
      <Table>
        <thead>
          <TR $hideBorders>
            <TH>
              <SkeletonText width="42px" />
            </TH>
            <TH>
              <SkeletonText width="46px" />
            </TH>
          </TR>
        </thead>
        <tbody>
          <TR>
            <LeftTD>
              <SkeletonText width="70px" />
            </LeftTD>
            <RightTD>
              <SkeletonText width="300px" />
            </RightTD>
          </TR>
          <TR>
            <LeftTD>
              <SkeletonText width="45px" />
            </LeftTD>
            <RightTD>
              <SkeletonText width="230px" />
            </RightTD>
          </TR>
          <TR>
            <LeftTD>
              <SkeletonText width="150px" />
            </LeftTD>
            <RightTD>
              <SkeletonText width="500px" />
            </RightTD>
          </TR>
          <TR>
            <LeftTD>
              <SkeletonText width="100px" />
            </LeftTD>
            <RightTD>
              <SkeletonText width="286px" />
            </RightTD>
          </TR>
          <TR>
            <LeftTD>
              <SkeletonText width="88px" />
            </LeftTD>
            <RightTD>
              <SkeletonText width="450px" />
            </RightTD>
          </TR>
        </tbody>
      </Table>
    </div>
  );
};

export {VariblesSkeleton};
