( function _Definition_s_()
{

'use strict';

/**
* Collection of definitions for constructions.
* @namespace wTools.define
* @extends Tools
* @module Tools/base/Proto
*/

const Self = _global_.wTools;
const _global = _global_;
const _ = _global_.wTools;

// --
// implement
// --

/**
* @classdesc Class container for creating property-like entity from non-primitive value.
  Is used by routines:
  @see {@link module:Tools/base/Proto.wTools.define.own}
  @see {@link module:Tools/base/Proto.wTools.define.common}
  @see {@link module:Tools/base/Proto.wTools.define.instanceOf}
  @see {@link module:Tools/base/Proto.wTools.define.makeWith}
  @see {@link module:Tools/base/Proto.wTools.define.contained}
* @class Definition
* @namespace Tools.define
* @module Tools/base/Proto
*/

function Definition( o )
{
  _.assert( arguments.length === 1 );
  if( !( this instanceof Definition ) )
  if( o instanceof Definition )
  return o;
  else
  return new( _.constructorJoin( Definition, arguments ) );
  _.props.extend( this, o );
  _.assert( _.longHas( _.definition.DefinitionGroup, o.defGroup ) );
  return this;
}

Object.setPrototypeOf( Definition, null );
Definition.prototype = Object.create( null );
Definition.prototype.constructor = Definition;
Definition.prototype.cloneShallow = function()
{
  if( this._blueprint === false )
  {
    _.assert( Object.isFrozen( this ) );
    return this;
  }

  // let result = new Definition( this );
  let result = Object.create( Object.getPrototypeOf( this ) );
  // debugger;
  Object.assign( result, this );
  // debugger;

  if( result._blueprint )
  result._blueprint = null;
  if( result._ )
  result._ = Object.create( null );

  // if( result._blueprint === false )
  // Object.freeze( result );
  // else
  Object.preventExtensions( result );

  return result;
}

Definition.prototype.cloneDeep = function()
{
  return this.cloneShallow();
}

//

function retype( o )
{
  _.assert( _.mapIs( o ) );
  Object.setPrototypeOf( o, Definition.prototype );
  return o;
}

//

function toVal( definition )
{
  _.assert( _.definitionIs( definition ) );
  _.assert( _.routineIs( definition.toVal ) );
  _.assert( definition.val !== undefined );
  return definition.toVal( definition.val );
}

//

function nameOf( definition )
{
  _.assert( _.definition.is( definition ) );
  return `${definition.name || ''}`;
}

//

function qnameOf( definition )
{
  _.assert( _.definition.is( definition ) );
  let result = `${definition.defGroup}::${definition.kind}`;
  if( definition.name !== undefined )
  result += `::${definition.name || ''}`;
  return result
}

//

function _make( o )
{

  _.assert( arguments.length === 1 );
  _.assert( _.strDefined( o.kind ) );
  _.assert( _.strDefined( o.defGroup ) );
  _.assert( _.mapIs( o ) );
  _.assert( o._blueprint === null || o._blueprint === false || _.blueprint.isDefinitive( o._blueprint ) );

  if( o._blueprint === undefined )
  o._blueprint = null;

  // let definition = new _.Definition( o );
  let definition = _.definition.retype( o );
  _.assert( definition === o );
  _.assert( definition.blueprintAmend === undefined );
  _.assert( definition.constructionAmend === undefined );
  _.assert( definition.blueprint === undefined );

  if( definition._blueprint === false )
  Object.freeze( definition );
  else
  Object.preventExtensions( definition );

  return definition;
}

_make.defaults =
{
  kind : null,
  defGroup : null,
  _blueprint : null,
}

//

function _unnamedMake( o )
{
  _.assert( arguments.length === 1 );
  _.assert( !o.defGroup || o.defGroup === 'definition.unnamed' );
  if( !o.defGroup )
  o.defGroup = 'definition.unnamed';
  return _.definition._make( o );
}

//

function _namedMake( o )
{
  _.assert( arguments.length === 1 );
  _.assert( !o.defGroup || o.defGroup === 'definition.named' );
  if( !o.defGroup )
  o.defGroup = 'definition.named';
  return _.definition._make( o );
}

//

function _traitMake( o )
{
  _.assert( arguments.length === 1 );
  _.assert( !o.defGroup || o.defGroup === 'trait' );
  if( !o.defGroup )
  o.defGroup = 'trait';
  return _.definition._make( o );
}

//

function extend( src )
{
  _.assert( _.mapIs( src ) );

  for( let name in src )
  {
    _.assert
    (
      name === src[ name ].name,
      () => `Name of definition should be same as its alias, but \`${name} <> ${src[ name ].name}\``
    );
    _.definition._extend( src[ name ] );
  }

}

//

function _extend( src )
{

  _.assert( _.routineIs( src ) );
  _.assert( _.strIs( src.name ) );
  _.assert( arguments.length === 1 );

  _.assert
  (
    _.mapIs( src.identity ),
    () => `Expects defined \`identity\`, but routine::${src.name} does not have such`
  );
  _.assert( !!src.identity.definition );
  _.assert( _.define.trait === _.trait );

  if( src.identity.trait )
  {
    _.define.trait[ src.name ] = src;
    _.define[ src.name ] = src;
  }
  else
  {
    _.define[ src.name ] = src;
    if( src.identity.named )
    _.define.named[ src.name ] = src;
    else
    _.define.unnamed[ src.name ] = src;
  }

}

// --
// define
// --

/**
* Routines to manipulate definitions.
* @namespace wTools.definition
* @extends Tools
* @module Tools/base/Proto
*/

let DefinitionExtension =
{

  // implementation

  is : _.definitionIs,
  retype,
  toVal,
  nameOf,
  qnameOf,
  _make,
  _unnamedMake,
  _namedMake,
  _traitMake,
  extend,
  _extend,

  // fields

}

_.define = _.define || Object.create( null );
_.define.named = _.define.named || Object.create( null );
_.define.unnamed = _.define.unnamed || Object.create( null );
_.define.trait = _.define.trait || Object.create( null );
_.trait = _.define.trait;

_.definition = _.definition || Object.create( null );
/* _.props.extend */Object.assign( _.definition, DefinitionExtension );
_.assert( _.routineIs( _.definitionIs ) );
_.assert( _.definition.is === _.definitionIs );

//

let ToolsExtension =
{
  Definition,
}

_.props.extend( _, ToolsExtension );

// --
// export
// --

if( typeof module !== 'undefined' )
module[ 'exports' ] = Self;

})();
