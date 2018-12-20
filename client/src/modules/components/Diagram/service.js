import {Colors, themeStyle} from 'modules/theme';

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

export function getOverlaysByState(statistics) {
  const overlays = {
    active: [],
    completed: [],
    canceled: [],
    incidents: []
  };

  statistics.forEach(statistic => {
    const states = ['active', 'completed', 'incidents', 'canceled'];

    states.forEach(state => {
      if (statistic[state] > 0) {
        overlays[state].push({
          id: statistic.activityId,
          count: statistic[state]
        });
      }
    });
  });

  return overlays;
}
