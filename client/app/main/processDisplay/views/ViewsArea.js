import {createViewUtilsComponentFromReact} from 'reactAdapter';
import {createDefinitionCases} from './createDefinitionCases';

export const ViewsAreaReact = ({isViewSelected, areaComponent, views}) => {
  return createDefinitionCases(areaComponent, isViewSelected, views);
};

export const ViewsArea = createViewUtilsComponentFromReact('div', ViewsAreaReact);
