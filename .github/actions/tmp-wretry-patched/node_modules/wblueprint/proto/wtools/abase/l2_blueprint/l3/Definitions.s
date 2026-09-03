( function _Definitions_s_()
{

'use strict';

const Self = _global_.wTools;
const _global = _global_;
const _ = _global_.wTools;

//

let PropOptionsLogic =
{
  order           : 0,
  // before          : null,
  // after           : null,
  blueprintDepthLimit : null,
  blueprintDepthReserve : 0,
  _blueprint : false,
}

let PropOptionsDescriptor =
{
  static          : 0,
  enumerable      : null,
  configurable    : null,
  writable        : null,
}

let PropOptionsMove =
{
  collection      : 'scalar',
  insToIns        : 'val',
  datToIns        : 'val',
  insToDat        : 'val',
  datToDat        : 'val',
  valToIns        : 'val',
  // relation        : null,
}

let PropOptionsAccessor =
{
  accessor        : null,
  methods         : null,
  storingStrategy : 'underscore',
  combining       : null,
  addingMethods   : null,
  // ... _.accessor.AmethodTypesMap,
}

// --
// collection
// --

function _singleArgumentHead( routine, args )
{
  let o = args[ 0 ];
  if( !o )
  o = Object.create( null );

  o = _.routine.options_( routine, o );

  _.assert( arguments.length === 2 );
  _.assert( args.length === 0 || args.length === 1, () => `Expects optional argument, but got ${args.length} arguments` );
  _.assert( _.mapIs( o ) );

  return o;
}

//

function _pairArgumentsHead( routine, args )
{
  let o = args[ 1 ];

  if( o )
  o.val = args[ 0 ];
  else
  o = { val : args[ 0 ] };

  o = _.routine.options_( routine, o );

  _.assert( arguments.length === 2 );
  _.assert( args.length === 1 || args.length === 2 );
  _.assert( args[ 1 ] === undefined || _.mapIs( args[ 1 ] ) );

  return o;
}

//

let _toVal = Object.create( null );
_toVal.val = function val( val ) { return val }
_toVal.shallow = function shallow( val ) { return _.entity.cloneShallow( val ) }
// _toVal.deep = function deep( val ) { return _.entity.cloneDeep({ src : val }) }
_toVal.deep = function deep( val ) { return _.entity.cloneDeep( val ) }
_toVal.call = function call( val ) { return val() }
_toVal.new = function nw( val ) { return new val() }

//

function prop_head( routine, args )
{
  let o = args[ 1 ];

  if( !o )
  o = { val : args[ 0 ] };
  else if( args[ 0 ] !== undefined )
  o.val = args[ 0 ];

  if( _.boolLike( o.accessor ) )
  o.accessor = !!o.accessor;

  o.accessor = _.accessor.suiteMove( o.accessor, o ) || routine.defaults.accessor;

  o = _.routine.options_( routine, o );

  if( _.boolLike( o.writable ) )
  o.writable = !!o.writable;
  if( o.configurable === null )
  o.configurable = true;
  else
  o.configurable = !!o.configurable;

  if( o.enumerable === null )
  o.enumerable = !o.static;
  else
  o.enumerable = !!o.enumerable;

  if( o.static === null )
  o.static = false;
  else if( _.boolLike( o.static ) )
  o.static = !!o.static;

  if( o.blueprintDepthLimit === null )
  o.blueprintDepthLimit = o.static ? 1 : 0;

  _.assert( arguments.length === 2 );
  _.assert( args.length === 1 || args.length === 2 );
  _.assert( args[ 1 ] === undefined || _.mapIs( args[ 1 ] ) );

  _.assert( _.mapIs( o ) );

  _.assert( _.strIs( o.valToIns ) );
  _.assert( _.longHas( [ 'scalar', 'map', 'enumerable' ], o.collection ) );
  _.assert( 'scalar' === o.collection, 'not implemented' ); /* zzz : implement */
  _.assert( _.longHas( [ 'no', 'val', 'shallow', 'deep', 'call', 'new' ], o.valToIns ) );

  _.assert( o.collection === 'scalar', 'not implemented' );
  _.assert( o.insToIns === 'val', 'not implemented' );
  _.assert( o.datToIns === 'val', 'not implemented' );
  _.assert( o.insToDat === 'val', 'not implemented' );
  _.assert( o.datToDat === 'val', 'not implemented' );

  _.assert( o.blueprintDepthLimit >= 0 );
  _.assert( _.boolIs( o.writable ) || o.writable === null );
  _.assert( _.boolIs( o.configurable ) );
  _.assert( _.boolIs( o.enumerable ) );
  _.assert( _.fuzzyIs( o.static ) );

  return o;
}

//

function prop_body( o )
{

  _.map.assertHasAll( o, prop_body.defaults );
  _.assert( o.val !== undefined );
  _.assert( o.blueprintDepthLimit >= 0 );
  _.assert( _.boolIs( o.writable ) || o.writable === null );
  _.assert( _.boolIs( o.configurable ) );
  _.assert( _.boolIs( o.enumerable ) );
  _.assert( o._blueprint !== undefined );

  const toVal = o.toVal = _toVal[ o.valToIns ];
  _.assert( _.routineIs( toVal ), () => `Unknown toVal::${o.valToIns} )` );

  o.blueprintForm1 = blueprintForm1;
  o.blueprintForm2 = blueprintForm2;
  o.kind = 'prop';
  return _.definition._namedMake( o );

  /* -

- blueprintForm2
- declareStaticWithAccessor
- declareStaticWithoutAccessor
- declareWithAccessor
- declareOrdinary
- declareUnordinary
- valFrom

  */

  function blueprintForm1( op )
  {
    _.assert( _.strDefined( op.propName ) || _.strDefined( op.definition.name ) );
    _.assert( op.propName === null || op.propName === op.definition.name );
    Object.freeze( op.definition );
  }

  function blueprintForm2( op )
  {

    _.assert( _.strDefined( op.propName ) || _.strDefined( op.definition.name ) );
    _.assert( op.propName === null || op.propName === op.definition.name );

    if( op.definition.static )
    {
      let op2 =
      {
        definition : op.definition,
        blueprint : op.blueprint,
        name : op.propName,
        amending : op.amending,
      }
      if( op.definition.accessor )
      declareStaticWithAccessor( op2 );
      else
      declareStaticWithoutAccessor( op2 );

      if( op.definition.static !== _.maybe )
      return;
      if( op.blueprint.traitsMap.typed.val === true )
      return;
    }

    if( op.definition.accessor )
    {
      declareWithAccessor( op.blueprint, op.definition );
    }
    else if
    (
         op.definition.static === true
      && op.definition.valToIns === 'val'
      && op.definition.enumerable
      && op.definition.configurable
      && ( op.definition.writable || op.definition.writable === null )
    )
    {
      if( op.definition.val === _.nothing )
      op.blueprint.propsSupplementation[ op.propName ] = undefined;
      else
      op.blueprint.propsExtension[ op.propName ] = _.escape.right( op.definition.val );
    }
    else if
    (
      op.definition.enumerable && op.definition.configurable && ( op.definition.writable || op.definition.writable === null )
    )
    {
      declareOrdinary( op.blueprint, op.definition );
    }
    else
    {
      declareUnordinary( op.blueprint, op.definition );
    }

  }

  /* */

  function declareStaticWithAccessor( op )
  {
    const prototype = op.blueprint.prototype;
    const name = op.name;
    const val = op.definition.val;

    if( prototype === null )
    return op.blueprint;

    let opts =
    {
      enumerable : op.definition.enumerable,
      configurable : op.definition.configurable,
    };

    _.assert( _.boolIs( op.definition.configurable ) );
    _.assert( _.boolIs( op.definition.enumerable ) );
    _.assert( !_.boolLikeFalse( op.definition.accessor ) );
    _.assert( !_.prototype._ofStandardEntity( prototype ), 'Attempt to pollute _global_.Object.prototype' );

    // if( _global_.debugger )
    // debugger;

    let val2 = valFrom( prototype, name, op.amending, val );

    let op2 =
    {
      name : op.name,
      object : prototype,
      methods : op.definition.methods,
      suite : op.definition.accessor,
      val : val2,
      storingStrategy : op.definition.storingStrategy,
      enumerable : op.definition.enumerable,
      configurable : op.definition.configurable,
      writable : op.definition.writable,
      combining : op.definition.combining,
      addingMethods : op.definition.addingMethods,
    }

    op2.needed = _.accessor._declaringIsNeeded( op2 );
    if( op2.needed )
    {
      _.accessor.suiteNormalize( op2 );
      for( let mname in _.accessor.AmethodTypesMap )
      if( op2.normalizedAsuite[ mname ] )
      op2.normalizedAsuite[ mname ] = _.routineJoin( prototype, op2.normalizedAsuite[ mname ] );
      _.accessor.declareSingle.body( op2 );
    }

    opts.get = () =>
    {
      return prototype[ name ];
    }

    if( op.definition.writable || op.definition.writable === null )
    {
      opts.set = ( src ) =>
      {
        prototype[ name ] = src;
      }
    }

    Object.defineProperty( op.blueprint.make, name, opts );

    return op.blueprint;
  }

  /* */

  function declareStaticWithoutAccessor( op )
  {
    const prototype = op.blueprint.prototype;
    const name = op.name;
    const enumerable = op.definition.enumerable;
    const configurable = op.definition.configurable;
    const writable = op.definition.writable;
    const toVal = op.definition.toVal;
    const val = op.definition.val;

    let opts =
    {
      enumerable,
      configurable,
    };

    _.assert( _.boolIs( op.definition.configurable ) );
    _.assert( _.boolIs( op.definition.enumerable ) );
    _.assert( !_.prototype._ofStandardEntity( prototype ), 'Attempt to pollute _global_.Object.prototype' );

    // if( _global_.debugger )
    // debugger;

    if( prototype )
    {
      let val2 = _.escape.right( valFrom( prototype, name, op.amending, val ) );

      if( op.definition.writable || op.definition.writable === null )
      {
        opts.get = () =>
        {
          return val2;
        }
        opts.set = ( src ) =>
        {
          val2 = src;
          return src;
        }
      }
      else
      {
        opts.writable = false;
        opts.value = val2;
      }
      Object.defineProperty( op.blueprint.make, name, opts );
      if( prototype !== null )
      Object.defineProperty( prototype, name, opts );
    }

    return op.blueprint;
  }

  /* */

  function declareWithAccessor( blueprint, definition )
  {
    const toVal = definition.toVal;
    const name = definition.name;
    const methods = definition.methods;
    const storingStrategy = definition.storingStrategy;
    const enumerable = definition.enumerable;
    const configurable = definition.configurable;
    const writable = definition.writable;
    const combining = definition.combining;
    const addingMethods = definition.addingMethods;
    const accessor = definition.accessor;
    const val = definition.val;
    const isStatic = definition.static;
    const prototype = blueprint.prototype;
    let op2, normalizedAsuite;

    _.assert( _.fuzzyIs( blueprint.typed ) );

    // if( _global_.debugger )
    // debugger;

    if( !isStatic && blueprint.traitsMap.typed.val && blueprint.prototype )
    {
      op2 = _.accessor.declareSingle
      ({
        name,
        object : blueprint.prototype,
        methods,
        suite : accessor ? _.props.extend( null, accessor ) : false,
        storingStrategy,
        storageIniting : true,
        valueGetting : false,
        valueSetting : false,
        enumerable,
        configurable,
        writable,
        combining,
        addingMethods,
      });
      normalizedAsuite = op2.normalizedAsuite;
    }

    let constructionInit;
    if( isStatic )
    constructionInit = constructionInitUntypedStatic;
    else if( blueprint.traitsMap.typed.val === _.maybe )
    constructionInit = constructionInitMaybe;
    else if( blueprint.traitsMap.typed.val === true && blueprint.prototype )
    constructionInit = constructionInitTyped;
    else if
    (
      blueprint.traitsMap.typed.val === false
      || blueprint.prototype === null
      || blueprint.traitsMap.typed.val
      && !blueprint.prototype
    )
    constructionInit = constructionInitUntyped;
    else _.assert( 0 );

    _.blueprint._practiceAdd( blueprint, 'constructionInit', constructionInit );

    function constructionInitTyped( genesis )
    {
      // if( _global_.debugger )
      // debugger;
      _.accessor._objectInitStorage( genesis.construction, normalizedAsuite );

      let val2 = valFrom( genesis.construction, name, genesis.amending, val );
      if( val2 !== _.nothing )
      _.accessor._objectSetValue
      ({
        object : genesis.construction,
        normalizedAsuite,
        storingStrategy,
        name,
        val : val2,
      });

    }

    function constructionInitUntyped( genesis )
    {
      // if( _global_.debugger )
      // debugger;

      let val2 = valFrom( genesis.construction, name, genesis.amending, val );
      _.accessor.declareSingle
      ({
        object : genesis.construction,
        methods,
        suite : accessor ? _.props.extend( null, accessor ) : false,
        storingStrategy,
        name,
        val : val2,
        enumerable,
        configurable,
        writable,
        combining,
        addingMethods,
      });
    }

    function constructionInitUntypedStatic( genesis )
    {
      // if( _global_.debugger )
      // debugger;

      let prototype2 = Object.getPrototypeOf( genesis.construction );
      if( prototype2 && prototype2 === prototype )
      return;

      let val2 = valFrom( genesis.construction, name, genesis.amending, val );
      _.accessor.declareSingle
      ({
        object : genesis.construction,
        methods,
        suite : accessor ? _.props.extend( null, accessor ) : false,
        storingStrategy,
        name,
        val : val2,
        enumerable,
        configurable,
        writable,
        combining,
        addingMethods,
      });
    }

    function constructionInitMaybe( genesis )
    {
      // if( _global_.debugger )
      // debugger;
      if( prototype === null )
      {
        constructionInitUntyped( genesis );
        return;
      }
      let prototype2 = Object.getPrototypeOf( genesis.construction );
      if( prototype2 === prototype )
      {
        constructionInitTyped( genesis );
      }
      else
      {
        constructionInitUntyped( genesis );
      }
    }

  }

  /* */

  function declareOrdinary( blueprint, definition )
  {
    const toVal = definition.toVal;
    const name = definition.name;
    const isStatic = definition.static;
    const prototype = blueprint.prototype;
    const val = definition.val;

    _.blueprint._practiceAdd( blueprint, 'constructionInit', constructionInit );

    function constructionInit( genesis )
    {
      // if( _global_.debugger )
      // debugger;

      if( isStatic )
      {
        let prototype2 = Object.getPrototypeOf( genesis.construction );
        if( prototype2 && prototype2 === prototype )
        return;
      }

      let val2 = _.escape.right( valFrom( genesis.construction, name, genesis.amending, val ) );
      /* it is important to set even if value as it was before setting. property could be owned by prototype */
      genesis.construction[ name ] = val2;
    }
  }

  /* */

  function declareUnordinary( blueprint, definition )
  {
    const enumerable = definition.enumerable;
    const configurable = definition.configurable;
    const writable = definition.writable === null ? true : definition.writable;
    const toVal = definition.toVal;
    const name = definition.name;
    const isStatic = definition.static;
    const val = definition.val;
    const prototype = blueprint.prototype;

    _.blueprint._practiceAdd( blueprint, 'constructionInit', constructionInit );

    function constructionInit( genesis )
    {
      // if( _global_.debugger )
      // debugger;

      if( isStatic )
      {
        let prototype2 = Object.getPrototypeOf( genesis.construction );
        if( prototype2 && prototype2 === prototype )
        return;
      }

      const opts =
      {
        enumerable,
        configurable,
        writable,
      };

      let val2 = _.escape.right( valFrom( genesis.construction, name, genesis.amending, val ) );
      opts.value = val2;
      Object.defineProperty( genesis.construction, name, opts );
    }
  }

  /* */

  function valFrom( /* object, name, amending, val */ )
  {
    let object = arguments[ 0 ];
    let name = arguments[ 1 ];
    let amending = arguments[ 2 ];
    let val = arguments[ 3 ];
    let val2;
    if( amending === 'supplement' && Object.hasOwnProperty.call( object, name ) )
    {
      val2 = _.escape.left( object[ name ] );
    }
    else if( val === _.nothing )
    {
      if( Object.hasOwnProperty.call( object, name ) )
      {
        val2 = _.escape.left( object[ name ] );
      }
      else /* keep nothing for declareSingle */
      {
        val2 = _.nothing;
      }
    }
    else
    {
      val2 = _.escape.left( toVal( _.escape.right( val ) ) );
    }
    return val2;
  }

  /* */

}

prop_body.defaults =
{

  ... PropOptionsLogic,
  /*
  order           : 0,
  // before          : null,
  // after           : null,
  blueprintDepthLimit : null,
  blueprintDepthReserve : 0,
  _blueprint : false,
  */

  ... PropOptionsDescriptor,
  /*
  static          : 0,
  enumerable      : null,
  configurable    : null,
  writable        : null,
  */

  ... PropOptionsMove,
  /*
  collection      : 'scalar',
  insToIns        : 'val',
  datToIns        : 'val',
  insToDat        : 'val',
  datToDat        : 'val',
  valToIns        : 'val',
  // relation        : null,
  */

  ... PropOptionsAccessor,
  /*
  accessor        : null,
  methods         : null,
  storingStrategy : 'underscore',
  // grab            : null,
  // get             : null,
  // put             : null,
  // set             : null,
  */

  name            : null,
  val             : _.nothing,
  _blueprint       : null,

}

prop_body.identity = { definition : true, named : true };

let prop = _.routine.uniteCloning_replaceByUnite( prop_head, prop_body );
_.routine.er( prop, _singleArgumentHead );

/*
|                | Composes | Aggregates | Associates  |  Restricts  |  Medials  |   Statics   |
| -------------- |:--------:|:----------:|:-----------:|:-----------:|:---------:|:-----------:|
| Static         |          |            |             |             |           |      +      |
| Ins to Ins     |   deep   |    val     |    val      |      -      |     -     |             |
| Dat to Ins     |   deep   |    deep    |    val      |      -      |   val     |             |
| Ins to Dat     |   deep   |    deep    |    val      |      -      |     -     |             |
| Dat to Dat     |   deep   |    deep    |    val      |      -      |   val     |             |
*/

/*
order           : [ -10 .. +10 ]                                                                            @default : 0
static          : [ 0 , 1 ]                                                                                 @default : 0
enumerable      : [ 0 , 1 ]                                                                                 @default : 1
configurable    : [ 0 , 1 ]                                                                                 @default : 1
writable        : [ 0 , 1 ]                                                                                 @default : 1
toVal           : routine                                                                                   @default : null
collection      : [ scalar , array , map ]                                                                  @default : scalar
insToIns        : [ val , shallow , deep ]                                                                  @default : val
datToIns        : [ val , shallow , deep ]                                                                  @default : val
insToDat        : [ val , shallow , deep ]                                                                  @default : val
datToDat        : [ val , shallow , deep ]                                                                  @default : val
valToIns        : [ val , shallow , deep , call , new ]                                                     @default : val
relation        : [ null , composes , aggregates , associates , restricts , medials , statics , copiers ]   @default : null
val             : *                                                                                         @default : null
*/

//

function props_body( o )
{
  let self = this;

  if( _.longIs( o.val ) )
  {
    return _.container.map_( o.val, ( e ) => mapEach( e ) );
  }
  else
  {
    return mapEach( o.val );
  }

  function mapEach( map )
  {
    _.assert( _.mapIs( map ) );
    let r = _.container.map_( map, ( e, k ) =>
    {
      let o2 = _.props.extend( null, o );
      o2.val = e;
      _.assert( o2.name === null );
      o2.name = k;
      let r = _.define.prop.body.call( self, o2 );
      _.assert( r.name === k );
      return r;
    });
    r = _.props.vals( r );
    return r;
  }

}

props_body.defaults =
{
  ... prop.defaults,
}

let props = _.routine.uniteCloning_replaceByUnite( prop_head, props_body );
props.identity = _.props.extend( null, prop.identity );
props.identity.named = false;
_.routine.er( props, _singleArgumentHead );

//

function val_body( o )
{
  return _.define.prop.body.call( this, o );
}

val_body.defaults =
{
  ... prop.defaults,
  valToIns : 'val',
}

let val = _.routine.uniteCloning_replaceByUnite( prop_head, val_body );
val.identity = _.props.extend( null, prop.identity );
_.routine.er( val, _singleArgumentHead );

//

function vals_body( o )
{
  return _.define.props.body.call( this, o );
}

vals_body.defaults =
{
  ... prop.defaults,
  valToIns : 'val',
}

let vals = _.routine.uniteCloning_replaceByUnite( prop_head, vals_body );
vals.identity = _.props.extend( null, val.identity );
vals.identity.named = false;
_.routine.er( vals, _singleArgumentHead );

//

function shallow_body( o )
{
  return _.define.prop.body.call( this, o );
}

shallow_body.defaults =
{
  ... prop.defaults,
  valToIns : 'shallow',
}

let shallow = _.routine.uniteCloning_replaceByUnite( prop_head, shallow_body );
shallow.identity = _.props.extend( null, prop.identity );
_.routine.er( shallow, _singleArgumentHead );

//

function shallows_body( o )
{
  return _.define.props.body.call( this, o );
}

shallows_body.defaults =
{
  ... prop.defaults,
  valToIns : 'shallow',
}

let shallows = _.routine.uniteCloning_replaceByUnite( prop_head, shallows_body );
shallows.identity = _.props.extend( null, shallow.identity );
shallows.identity.named = false;
_.routine.er( shallows, _singleArgumentHead );

//

function deep_body( o )
{
  return _.define.prop.body.call( this, o );
}

deep_body.defaults =
{
  ... prop.defaults,
  valToIns : 'deep',
}

let deep = _.routine.uniteCloning_replaceByUnite( prop_head, deep_body );
deep.identity = _.props.extend( null, prop.identity );
_.routine.er( deep, _singleArgumentHead );

//

function deeps_body( o )
{
  return _.define.props.body.call( this, o );
}

deeps_body.defaults =
{
  ... prop.defaults,
  valToIns : 'deep',
}

let deeps = _.routine.uniteCloning_replaceByUnite( prop_head, deeps_body );
deeps.identity = _.props.extend( null, deep.identity );
deeps.identity.named = false;
_.routine.er( deeps, _singleArgumentHead );

//

function call_body( o )
{
  return _.define.prop.body.call( this, o );
}

call_body.defaults =
{
  ... prop.defaults,
  valToIns : 'call',
}

let call = _.routine.uniteCloning_replaceByUnite( prop_head, call_body );
call.identity = _.props.extend( null, prop.identity );
_.routine.er( call, _singleArgumentHead );

//

function calls_body( o )
{
  return _.define.props.body.call( this, o );
}

calls_body.defaults =
{
  ... prop.defaults,
  valToIns : 'call',
}

let calls = _.routine.uniteCloning_replaceByUnite( prop_head, calls_body );
calls.identity = _.props.extend( null, call.identity );
calls.identity.named = false;
_.routine.er( calls, _singleArgumentHead );

//

function new_body( o )
{
  return _.define.prop.body.call( this, o );
}

new_body.defaults =
{
  ... prop.defaults,
  valToIns : 'new',
}

let _new = _.routine.uniteCloning_replaceByUnite( prop_head, new_body );
_new.identity = _.props.extend( null, prop.identity );
_.routine.er( _new, _singleArgumentHead );

//

function news_body( o )
{
  return _.define.props.body.call( this, o );
}

news_body.defaults =
{
  ... prop.defaults,
  valToIns : 'new',
}

let _news = _.routine.uniteCloning_replaceByUnite( prop_head, news_body );
_news.identity = _.props.extend( null, _new.identity );
_news.identity.named = false;
_.routine.er( _news, _singleArgumentHead );

//

function static_body( o )
{
  return _.define.prop.body.call( this, o );
}

static_body.defaults =
{
  ... prop.defaults,
  static : 1,
}

let _static = _.routine.uniteCloning_replaceByUnite( prop_head, static_body );
_static.identity = _.props.extend( null, prop.identity );
_.routine.er( _static, _singleArgumentHead );

//

function statics_body( o )
{
  return _.define.props.body.call( this, o );
}

statics_body.defaults =
{
  ... prop.defaults,
  static : 1,
}

let _statics = _.routine.uniteCloning_replaceByUnite( prop_head, statics_body );
_statics.identity = _.props.extend( null, _static.identity );
_statics.identity.named = false;
_.routine.er( _statics, _singleArgumentHead );

//

function alias_head( routine, args )
{
  let o = args[ 0 ];

  if( !_.mapIs( args[ 0 ] ) )
  o = { originalName : args[ 0 ] };

  _.assert( args.length === 1 );

  return _.define.prop.head( routine, [ undefined, o ] );
}

function alias_body( o )
{

  _.routine.assertOptions( alias_body, arguments );

  let originalContainer = o.originalContainer;
  let originalName = o.originalName;

  _.assert( originalContainer === null || !!originalContainer );
  _.assert( _.strDefined( originalName ), 'Expects defined `originalName`' );
  _.assert( o.val === undefined );
  _.assert( o.accessor === null || _.boolLikeTrue( o.accessor ) || _.mapIs( o.accessor ) );

  if( !_.mapIs( o.accessor ) )
  o.accessor = Object.create( null );

  if( originalContainer === null )
  {
    let accessor2 = { grab : selfGet, get : selfGet, put : selfSet, set : selfSet }
    _.accessor.suiteSupplement( o.accessor, accessor2 );
  }
  else
  {
    let accessor2 = { grab : get, get, put : set, set }
    _.accessor.suiteSupplement( o.accessor, accessor2 );
  }

  o.val = _.nothing;
  _.props.supplement( o, _.define.prop.defaults );
  return _.define.prop.body( o );

  /* */

  function get()
  {
    return originalContainer[ originalName ];
  }

  function set( src )
  {
    originalContainer[ originalName ] = src;
    return originalContainer[ originalName ];
  }

  function selfGet()
  {
    return this[ originalName ];
  }

  function selfSet( src )
  {
    this[ originalName ] = src;
    return this[ originalName ];
  }

}

alias_body.defaults =
{
  ... _.mapBut_( null, prop.defaults, { methods : null, val : null } ),
  originalContainer : null,
  originalName : null,
}

let alias = _.routine.uniteCloning_replaceByUnite( alias_head, alias_body );
alias.identity = _.props.extend( null, prop.identity );
_.routine.er( alias );

/* xxx : implement definition::aliases */

// --
//
// --

function nothing_functor()
{

  nothing_body.identity = { definition : true, named : false };
  nothing_body.defaults =
  {
    ... PropOptionsLogic,
    blueprintDepthReserve : -1,
    blueprintDepthLimit : 1,
    _blueprint : false,
  }

  let prototype = Object.create( null );
  prototype.defGroup = 'definition.unnamed';
  prototype.kind = 'nothing';
  prototype.name = null;
  _.props.extend( prototype, nothing_body.defaults );
  prototype.blueprintDefinitionRewrite = blueprintDefinitionRewrite;
  _.definition.retype( prototype );
  Object.freeze( prototype );

  let nothing = _.routine.uniteCloning_replaceByUnite( _head, nothing_body );
  return nothing;

  /* */

  function _head( routine, args )
  {
    let o = args[ 0 ];

    _.assert( arguments.length === 2 );
    _.assert( args.length === 0 || args.length === 1, () => `Expects optional argument, but got ${args.length} arguments` );
    _.assert( o === undefined || _.mapIs( o ) );

    if( !o )
    return prototype;

    _.map.assertHasOnly( o, routine.defaults );
    return o;
  }

  /* */

  function nothing_body( o )
  {
    if( o === prototype )
    return prototype;
    _.assert( o === undefined || _.mapIs( o ) || Object.getPrototypeOf( o ) === prototype );
    if( Object.getPrototypeOf( o ) !== prototype )
    Object.setPrototypeOf( o, prototype );
    Object.freeze( o );
    _.assert( arguments.length === 1 );
    return o;
  }

  /* */

  function blueprintDefinitionRewrite( op )
  {
  }

  /* */

}

let nothing = nothing_functor();

// function nothing_body( o )
// {
//
//   _.routine.assertOptions( nothing_body, arguments );
//   o.defGroup = 'definition.unnamed';
//   o.blueprintDefinitionRewrite = blueprintDefinitionRewrite;
//   o.kind = 'nothing';
//   return _.definition._unnamedMake( o );
//
//   function blueprintDefinitionRewrite( op )
//   {
//   }
//
// }
//
// nothing_body.defaults =
// {
//   ... PropOptionsLogic,
//   blueprintDepthReserve : -1,
//   blueprintDepthLimit : 1,
//   _blueprint : false,
// }
//
// nothing_body.identity = { definition : true, named : false };
//
// let nothing = _.routine.uniteCloning_replaceByUnite( _singleArgumentHead, nothing_body );

//

function _constant_functor()
{
  /* xxx : use predefined object for other definitions to optimize */

  let prototype = Object.create( null );
  prototype.defGroup = 'definition.named';
  prototype.kind = 'constant';
  prototype.subKind = 'constant';
  prototype.asAccessorSuite = asAccessorSuite;
  prototype.toVal = toVal;
  prototype.blueprintForm1 = blueprintForm1;
  prototype.blueprintForm2 = blueprintForm2;
  _.definition.retype( prototype );
  Object.freeze( prototype );

  let r =
  {
    'constant' : function( val )
    {
      return _constant( val );
    }
  }

  r.constant.identity = { definition : true, named : true };
  return r.constant;

  /* */

  function _constant( val )
  {
    let definition = Object.create( prototype );
    definition.val = val;
    definition.name = null;
    definition._blueprint = null;
    Object.preventExtensions( definition );
    _.assert( arguments.length === 1 );
    _.assert( definition.val !== undefined );
    return definition;
  }

  /* */

  function blueprintForm1( op )
  {
    const val = op.definition.val;
    const name = op.definition.name;
    _.assert( _.strDefined( op.propName ) || _.strDefined( op.definition.name ) );
    _.assert( op.propName === null || op.propName === op.definition.name );
    Object.freeze( op.definition );
  }

  /* */

  function blueprintForm2( op )
  {
    const val = op.definition.val;
    const name = op.definition.name;

    _.blueprint._practiceAdd( op.blueprint, 'constructionInit', constructionInitUntyped );

    function constructionInitUntyped( genesis )
    {
      // if( _global_.debugger )
      // debugger;

      let val2 = val;

      let opts =
      {
        enumerable : false,
        configurable : false,
        writable : false,
        value : val2,
      };

      Object.defineProperty( genesis.construction, name, opts );
    }

  }

  /* */

  function asAccessorSuite( op )
  {
    _.assert( _.definitionIs( op.amethod ) );
    return { get : op.amethod, set : false, put : false };
  }

  /* */

  function toVal( val )
  {
    _.assert( val !== undefined );
    return val;
  }

  /* */

}

let _constant = _constant_functor();

//

function _amendment_head( routine, args )
{
  let o = _pairArgumentsHead( ... arguments );
  _.assert( _.longHas( [ 'extend', 'supplement' ], o.amending ) );
  return o;
}

function _amendment_body( o )
{

  _.routine.assertOptions( _amendment_body, arguments );
  _.assert( _.object.isBasic( o.val ) );
  _.assert( _.blueprintIsDefinitive( o.val ) );
  o.defGroup = 'definition.unnamed';
  o.kind = 'amend';
  o.blueprintDefinitionRewrite = blueprintDefinitionRewrite;
  return _.definition._unnamedMake( o );

  function blueprintDefinitionRewrite( op )
  {
    let definition = op.definition;
    let blueprint = op.blueprint;
    return _.blueprint._amend
    ({
      blueprint : op.blueprint,
      blueprintDepth : op.blueprintDepth,
      extension : definition.val,
      amending : op.amending === 'extend' ? definition.amending : op.amending,
      blueprintComposing : 'amend',
      blueprintDepthReserve : definition.blueprintDepthReserve + op.blueprintDepthReserve,
    });
  }

}

_amendment_body.defaults =
{
  amending : null,
  val : null,
  blueprintDepthReserve : 0,
  blueprintDepthLimit : 1,
  _blueprint : false,
}

_amendment_body.identity = { definition : true, named : false };

let _amendment = _.routine.uniteCloning_replaceByUnite( _amendment_head, _amendment_body );

//

let extension = _.routine.uniteCloning_replaceByUnite({ head : _amendment_head, body : _amendment_body, name : 'extension' });

extension.defaults =
{
  ... _amendment.defaults,
  amending : 'extend',
}

_.assert( !!extension.identity.definition );
_.assert( !extension.identity.anemd );

_.routine.er( extension, _singleArgumentHead );

//

let supplementation = _.routine.uniteCloning_replaceByUnite({ head : _amendment_head, body : _amendment_body, name : 'supplementation' });

supplementation.defaults =
{
  ... _amendment.defaults,
  amending : 'supplement',
}

_.routine.er( supplementation, _singleArgumentHead );

//

function inherit_body( o )
{

  _.routine.assertOptions( inherit_body, arguments );
  _.assert( _.object.isBasic( o.val ) );
  _.assert( _.blueprintIsDefinitive( o.val ) );
  o.defGroup = 'definition.unnamed';
  o.kind = 'inherit';
  o.blueprintDefinitionRewrite = blueprintDefinitionRewrite;
  o._blueprint = false;
  return _.definition._unnamedMake( o );

  /* */

  function blueprintDefinitionRewrite( op )
  {
    _.assert( !op.primeDefinition || !op.secondaryDefinition, 'not tested' );
    let definition = op.primeDefinition || op.secondaryDefinition;
    let blueprint = op.blueprint;
    let add = op.amending === 'supplement' ? 'unshift' : 'push';

    // if( _global_.debugger )
    // debugger;

    let result = [];
    result[ add ]( definition.val );
    let prototype = null;
    if( definition.val.prototype )
    prototype = definition.val;

    if( definition.val.traitsMap.typed )
    {
      if( prototype )
      result[ add ]( _.trait.typed( definition.val.traitsMap.typed.val || true, { prototype, new : true, _synthetic : true } ) );
      else
      result[ add ]
      (
        _.trait.typed
        (
          definition.val.traitsMap.typed.val, { prototype : definition.val.traitsMap.typed.prototype, _synthetic : true }
        )
      );
    }

    return _.blueprint._amend
    ({
      blueprint : op.blueprint,
      blueprintDepth : op.blueprintDepth,
      extension : result,
      amending : op.amending,
      blueprintComposing : 'amend',
      blueprintDepthReserve : ( definition.blueprintDepthReserve || 0 ) + op.blueprintDepthReserve,
    });
  }

}

inherit_body.defaults =
{
  val : null,
}

inherit_body.identity = { definition : true, named : false };

let inherit = _.routine.uniteCloning_replaceByUnite( _pairArgumentsHead, inherit_body );

// --
//
// --

let DefinitionExtension =
{
  PropOptionsLogic,
  PropOptionsDescriptor,
  PropOptionsMove,
  PropOptionsAccessor,
}

/* _.props.extend */Object.assign( _.definition, DefinitionExtension );

let BlueprintExtension =
{

  _singleArgumentHead,
  _pairArgumentsHead,
  _toVal,

}

_.blueprint = _.blueprint || Object.create( null );
/* _.props.extend */Object.assign( _.blueprint, BlueprintExtension );

let DefineExtension =
{

  prop,
  props,
  val,
  vals,
  shallow,
  shallows,
  deep,
  deeps,
  call,
  calls,
  new : _new,
  news : _news,
  static : _static,
  statics : _statics,
  alias,

  nothing,
  constant : _constant,

  _amendment,
  extension,
  supplementation,
  inherit,


}

_.define = _.define || Object.create( null );
_.definition.extend( DefineExtension );

// --
// export
// --

if( typeof module !== 'undefined' )
module[ 'exports' ] = Self;

})();
