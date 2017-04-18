import {jsx, createStateComponent, withSelector} from 'view-utils';
import {createOverlaysRenderer} from './overlaysRenderer';
import {createTargetValueModal} from './TargetValueModal';

export const TargetValueDisplay = withSelector(({Diagram, processDefinition}) => {
  return (parentNode, eventsBus) => {
    const State = createStateComponent();
    const TargetValueModal = createTargetValueModal(State, processDefinition);

    const template = <State>
      <Diagram createOverlaysRenderer={createOverlaysRenderer(State, TargetValueModal)} />
      <TargetValueModal />
    </State>;

    return template(parentNode, eventsBus);
  };
});
