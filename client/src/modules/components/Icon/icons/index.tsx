/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactComponent as autorefresh} from './autorefresh.svg';
import {ReactComponent as check} from './check.svg';
import {ReactComponent as deleteIcon} from './delete.svg';
import {ReactComponent as exitFullscreen} from './exit-fullscreen.svg';
import {ReactComponent as fullscreen} from './fullscreen.svg';
import {ReactComponent as plus} from './plus.svg';
import {ReactComponent as minus} from './minus.svg';
import {ReactComponent as diagramReset} from './diagram-reset.svg';
import {ReactComponent as share} from './share.svg';
import {ReactComponent as edit} from './edit.svg';
import {ReactComponent as editSmall} from './edit-small.svg';
import {ReactComponent as close} from './close.svg';
import {ReactComponent as stop} from './stop.svg';
import {ReactComponent as link} from './link.svg';
import {ReactComponent as embed} from './embed.svg';
import {ReactComponent as save} from './save.svg';
import {ReactComponent as contextMenu} from './context-menu.svg';
import {ReactComponent as jump} from './jump.svg';
import {ReactComponent as copyDocument} from './copy-document.svg';
import {ReactComponent as copySmall} from './copy-small.svg';
import {ReactComponent as checkSmall} from './check-small.svg';
import {ReactComponent as left} from './left.svg';
import {ReactComponent as up} from './up.svg';
import {ReactComponent as right} from './right.svg';
import {ReactComponent as down} from './down.svg';
import {ReactComponent as settings} from './settings.svg';
import {ReactComponent as error} from './error.svg';
import {ReactComponent as warning} from './warning.svg';
import {ReactComponent as closeLarge} from './close-large.svg';
import {ReactComponent as closeSmall} from './close-small.svg';
import {ReactComponent as checkLarge} from './check-large.svg';
import {ReactComponent as checkCircle} from './check-circle.svg';
import {ReactComponent as collection} from './collection.svg';
import {ReactComponent as search} from './search.svg';
import {ReactComponent as searchReset} from './search-reset.svg';
import {ReactComponent as clear} from './clear.svg';
import {ReactComponent as publish} from './publish.svg';
import {ReactComponent as cancel} from './cancel.svg';
import {ReactComponent as arrowRight} from './arrow-right.svg';
import {ReactComponent as calender} from './calender.svg';
import {ReactComponent as user} from './user.svg';
import {ReactComponent as userGroup} from './user-group.svg';
import {ReactComponent as collapse} from './collapse.svg';
import {ReactComponent as expand} from './expand.svg';
import {ReactComponent as alert} from './alert.svg';
import {ReactComponent as dashboard} from './dashboard.svg';
import {ReactComponent as dataSource} from './data-source.svg';
import {ReactComponent as camundaSource} from './camunda-source.svg';
import {ReactComponent as report} from './report.svg';
import {ReactComponent as process} from './process.svg';
import {ReactComponent as filter} from './filter.svg';
import {ReactComponent as sortArrow} from './sort-arrow.svg';
import {ReactComponent as sortMenu} from './sort-menu.svg';
import {ReactComponent as info} from './info.svg';
import {ReactComponent as show} from './show.svg';
import {ReactComponent as hide} from './hide.svg';
import {ReactComponent as warningOutline} from './warning-outline.svg';
import {ReactComponent as infoOutline} from './info-outline.svg';
import {ReactComponent as questionMark} from './question-mark.svg';
import {ReactComponent as dashboardOptimize} from './dashboard-optimize.svg';
import {ReactComponent as dashboardOptimizeAccent} from './dashboard-optimize-accent.svg';
import {ReactComponent as optimize} from './optimize.svg';

export type IconSvg = React.FunctionComponent<React.SVGProps<SVGSVGElement>>;

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
  'user-group': userGroup,
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
  'dashboard-optimize-accent': dashboardOptimizeAccent,
  optimize,
};

export default icons;
