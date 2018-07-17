import {Colors, themeStyle} from 'modules/theme';
import {ACTIVITY_TYPE, UNNAMED_ACTIVITY} from 'modules/constants';

export function getDiagramColors(theme) {
  return {
    defaultFillColor: themeStyle({
      dark: Colors.uiDark02,
      light: Colors.uiLight04
    })({theme}),
    defaultStrokeColor: themeStyle({
      dark: Colors.darkDiagram,
      light: Colors.uiLight06
    })({theme})
  };
}

export function getElementType({businessObject, type}) {
  if (type === 'label') {
    return null;
  }
  if (businessObject.$instanceOf('bpmn:Task')) {
    return ACTIVITY_TYPE.TASK;
  }

  if (businessObject.$instanceOf('bpmn:Event')) {
    return ACTIVITY_TYPE.EVENT;
  }

  if (businessObject.$instanceOf('bpmn:Gateway')) {
    return ACTIVITY_TYPE.GATEWAY;
  }
}

/**
 * @returns { activityId : { name , type }}
 * @param {*} elementRegistry (bpmn elementRegistry)
 */
export function getActivitiesInfoMap(elementRegistry) {
  const activitiesInfoMap = {};

  elementRegistry.forEach(element => {
    const type = getElementType(element);
    if (!!type) {
      activitiesInfoMap[element.id] = {
        name: element.businessObject.name || UNNAMED_ACTIVITY,
        type
      };
    }
  });

  return activitiesInfoMap;
}
