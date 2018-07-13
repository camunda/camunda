import {Colors, themeStyle} from 'modules/theme';
import {ACTIVITY_TYPE} from 'modules/constants';

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
