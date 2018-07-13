const generateMockIcon = (displayName = 'Icon') => {
  const IconComponent = jest.fn();
  IconComponent.displayName = displayName;
  return IconComponent;
};

export const Batch = generateMockIcon('Batch');
export const StateIconIncident = generateMockIcon('StateIconIncident');
export const StateOk = generateMockIcon('StateOk');
export const StateCompleted = generateMockIcon('StateCompleted');
export const Stop = generateMockIcon('Stop');
export const Check = generateMockIcon('Check');
export const CloseLarge = generateMockIcon('CloseLarge');
export const Close = generateMockIcon('Close');
export const Date = generateMockIcon('Date');
export const Delete = generateMockIcon('Delete');
export const DiagramReset = generateMockIcon('DiagramReset');
export const Document = generateMockIcon('Document');
export const DownBar = generateMockIcon('DownBar');
export const Down = generateMockIcon('Down');
export const Edit = generateMockIcon('Edit');
export const FlownodeActivityIncident = generateMockIcon(
  'FlownodeActivityIncident'
);
export const FlownodeActivity = generateMockIcon('FlownodeActivity');
export const FlownodeActivityCompleted = generateMockIcon(
  'FlownodeActivityCompleted'
);
export const FlownodeEventCompleted = generateMockIcon(
  'FlownodeEventCompleted'
);
export const FlownodeEventIncident = generateMockIcon('FlownodeEventIncident');
export const FlownodeEvent = generateMockIcon('FlownodeEvent');
export const FlownodeGatewayCompleted = generateMockIcon(
  'FlownodeGatewayCompleted'
);
export const FlownodeGatewayIncident = generateMockIcon(
  'FlownodeGatewayIncident'
);
export const FlownodeGateway = generateMockIcon('FlownodeGateway');
export const InstanceHistoryIconCancelDark = generateMockIcon(
  'InstanceHistoryIconCancelDark'
);
export const InstanceHistoryIconCancelLight = generateMockIcon(
  'InstanceHistoryIconCancelLight'
);
export const InstanceHistoryIconEditDark = generateMockIcon(
  'InstanceHistoryIconEditDark'
);
export const InstanceHistoryIconEditLight = generateMockIcon(
  'InstanceHistoryIconEditLight'
);
export const InstanceHistoryIconErrorCancel = generateMockIcon(
  'InstanceHistoryIconErrorCancel'
);
export const InstanceHistoryIconErrorRetry = generateMockIcon(
  'InstanceHistoryIconErrorRetry'
);
export const InstanceHistoryIconIncidentActive = generateMockIcon(
  'InstanceHistoryIconIncidentActive'
);
export const InstanceHistoryIconIncidentDark = generateMockIcon(
  'InstanceHistoryIconIncidentDark'
);
export const InstanceHistoryIconIncidentLight = generateMockIcon(
  'InstanceHistoryIconIncidentLight'
);
export const InstanceHistoryIconRetryDark = generateMockIcon(
  'InstanceHistoryIconRetryDark'
);
export const InstanceHistoryIconRetryLight = generateMockIcon(
  'InstanceHistoryIconRetryLight'
);
export const LeftBar = generateMockIcon('LeftBar');
export const Left = generateMockIcon('Left');
export const Logo = generateMockIcon('Logo');
export const Minus = generateMockIcon('Minus');
export const PlusCircledSolid = generateMockIcon('PlusCircledSolid');
export const PlusCircled = generateMockIcon('PlusCircled');
export const Plus = generateMockIcon('Plus');
export const RemoveItem = generateMockIcon('RemoveItem');
export const Retry = generateMockIcon('Retry');
export const RightBar = generateMockIcon('RightBar');
export const Right = generateMockIcon('Right');
export const SelectAll = generateMockIcon('SelectAll');
export const StateIconFlownodeActivityIncident = generateMockIcon(
  'StateIconFlownodeActivityIncident'
);
export const StateIconGatewayIncident = generateMockIcon(
  'StateIconGatewayIncident'
);
export const UpBar = generateMockIcon('UpBar');
export const Up = generateMockIcon('Up');
export const Warning = generateMockIcon('Warning');
