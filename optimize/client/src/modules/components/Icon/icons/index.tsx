/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import autorefresh from './autorefresh.svg';
import check from './check.svg';
import deleteIcon from './delete.svg';
import exitFullscreen from './exit-fullscreen.svg';
import fullscreen from './fullscreen.svg';
import plus from './plus.svg';
import minus from './minus.svg';
import diagramReset from './diagram-reset.svg';
import share from './share.svg';
import edit from './edit.svg';
import editSmall from './edit-small.svg';
import close from './close.svg';
import stop from './stop.svg';
import link from './link.svg';
import embed from './embed.svg';
import save from './save.svg';
import contextMenu from './context-menu.svg';
import jump from './jump.svg';
import copyDocument from './copy-document.svg';
import copySmall from './copy-small.svg';
import checkSmall from './check-small.svg';
import left from './left.svg';
import up from './up.svg';
import right from './right.svg';
import down from './down.svg';
import settings from './settings.svg';
import error from './error.svg';
import warning from './warning.svg';
import closeLarge from './close-large.svg';
import closeSmall from './close-small.svg';
import checkLarge from './check-large.svg';
import checkCircle from './check-circle.svg';
import collection from './collection.svg';
import search from './search.svg';
import searchReset from './search-reset.svg';
import clear from './clear.svg';
import publish from './publish.svg';
import cancel from './cancel.svg';
import arrowRight from './arrow-right.svg';
import calender from './calender.svg';
import user from './user.svg';
import userGroup from './user-group.svg';
import collapse from './collapse.svg';
import expand from './expand.svg';
import alert from './alert.svg';
import dashboard from './dashboard.svg';
import dataSource from './data-source.svg';
import camundaSource from './camunda-source.svg';
import report from './report.svg';
import process from './process.svg';
import filter from './filter.svg';
import sortArrow from './sort-arrow.svg';
import sortMenu from './sort-menu.svg';
import info from './info.svg';
import show from './show.svg';
import hide from './hide.svg';
import warningOutline from './warning-outline.svg';
import infoOutline from './info-outline.svg';
import questionMark from './question-mark.svg';
import dashboardOptimize from './dashboard-optimize.svg';
import optimize from './optimize.svg';

export type IconSvg = React.FC<React.SVGProps<SVGElement>>;

export type Icons = {
  [key: string]: IconSvg;
};

const icons: Icons = {
  autorefresh,
  check,
  delete: deleteIcon,
  'exit-fullscreen': exitFullscreen,
  fullscreen,
  plus,
  minus,
  'diagram-reset': diagramReset,
  share,
  edit,
  'edit-small': editSmall,
  close,
  stop,
  link,
  embed,
  save,
  'context-menu': contextMenu,
  jump,
  'copy-document': copyDocument,
  'copy-small': copySmall,
  'check-small': checkSmall,
  left,
  up,
  right,
  down,
  settings,
  error,
  warning,
  'check-large': checkLarge,
  'check-circle': checkCircle,
  'close-large': closeLarge,
  'close-small': closeSmall,
  collection,
  search,
  'search-reset': searchReset,
  clear,
  publish,
  cancel,
  'arrow-right': arrowRight,
  calender,
  user,
  collapse,
  expand,
  alert,
  dashboard,
  'data-source': dataSource,
  report,
  process,
  filter,
  'camunda-source': camundaSource,
  'sort-arrow': sortArrow,
  'sort-menu': sortMenu,
  info,
  hide,
  show,
  'warning-outline': warningOutline,
  'info-outline': infoOutline,
  'question-mark': questionMark,
  'dashboard-optimize': dashboardOptimize,
  optimize,
};

export default icons;
