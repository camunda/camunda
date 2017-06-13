import {jsx, Match, withSelector} from 'view-utils';
import {createDefinitionCases} from './createDefinitionCases';

export const ViewsArea = withSelector(({isViewSelected, areaComponent}) => {
  return <Match>
  {
    createDefinitionCases(areaComponent, isViewSelected)
  }
  </Match>;
});
