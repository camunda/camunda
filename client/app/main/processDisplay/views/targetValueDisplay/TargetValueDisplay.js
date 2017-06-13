import {jsx, createStateComponent} from 'view-utils';
import {createDiagram} from 'widgets';
import {getDefinitionId} from 'main/processDisplay/service';
import {createOverlaysRenderer} from './overlaysRenderer';
import {createTargetValueModal} from './TargetValueModal';

const Diagram = createDiagram();

export const TargetValueDisplay = () => {
  return (parentNode, eventsBus) => {
    const State = createStateComponent();
    const TargetValueModal = createTargetValueModal(State, getDefinitionId, Diagram.getViewer);

    const template = <State>
      <Diagram createOverlaysRenderer={createOverlaysRenderer(State, TargetValueModal)} />
      <TargetValueModal />
    </State>;

    return template(parentNode, eventsBus);
  };
};
