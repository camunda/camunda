import {jsx, Children, createReferenceComponent} from 'view-utils';

export function Loader({children, className, style = 'position:static;'}) {
  return (node, eventsBus) => {
    const Reference = createReferenceComponent();
    const template = <div className={'loading_indicator overlay ' + className} style={style}>
      <div className="spinner"><span className="glyphicon glyphicon-refresh spin"></span></div>
      <div className="text">
        <Reference name="text" />
        loading
      </div>
      <Children children={children} />
      <Reference name="loader" />
    </div>;

    const update = template(node, eventsBus);
    const loaderNode = Reference.getNode('loader');
    const textNode = Reference.getNode('text');
    const lastNode = loaderNode.childNodes[loaderNode.childNodes.length - 1];

    if (textNode !== lastNode) {
      loaderNode.removeChild(textNode);
    }

    return update;
  };
}
