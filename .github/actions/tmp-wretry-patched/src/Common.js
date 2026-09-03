const core = require( '@actions/core' );
if( typeof wTools === 'undefined' )
require( '../node_modules/Joined.s' );
const _ = wTools;
const GithubActionsParser = require( 'github-actions-parser' );

//

function commandsForm( command )
{
  _.assert( command.length > 0, 'Please, specify Github action name or shell command.' );

  if( command[ 0 ] === '|' )
  {
    _.assert( command.length > 1, 'Expected multiline command.' );
    command.shift();
  }

  _.assert( !_.str.ends( command[ command.length - 1 ], /\s\\/ ), 'Last command should have no continuation.' );

  if( process.platform === 'win32' )
  {
    command.unshift( `$ErrorActionPreference = 'stop'` );
    command.push( 'if ((Test-Path -LiteralPath variable:\LASTEXITCODE)) { exit $LASTEXITCODE }' );
  }

  return command;
}

//

function remotePathForm( name, token )
{
  if( _.str.begins( name, [ './', 'docker:' ] ) )
  {
    _.assert( 0, 'unimplemented' );
  }
  else
  {
    name = name.replace( /^([^\/]+\/[^\/]+)\//, '$1.git/' );
    if( token )
    return _.git.path.parse( `https://oauth2:${ token }@github.com/${ _.str.replace( name, '@', '!' ) }` );
    else
    return _.git.path.parse( `https://github.com/${ _.str.replace( name, '@', '!' ) }` );
  }
}

//

function actionClone( localPath, remotePath )
{
  if( _.fileProvider.fileExists( localPath ) )
  return null;

  /* PATCH (camunda, INC-7723): upstream fetched the wrapped action with an anonymous
     `git clone`. git never sends URL credentials for a public repository -- GitHub answers the
     first, unauthenticated `info/refs` probe with 200, so git is never challenged -- which made
     every fetch anonymous regardless of `github_token`, and took out 33 CI jobs across three
     stable branches when GitHub throttled anonymous downloads. Fetch the tarball over the REST
     API instead: an `Authorization` header is sent unconditionally, so the request is genuinely
     authenticated. The tarball is already at the requested ref, so no tag checkout is needed. */

  const childProcess = require( 'child_process' );
  const fs = require( 'fs' );
  const os = require( 'os' );
  const nodePath = require( 'path' );

  const spec = process.env.RETRY_ACTION || '';
  /* owner/repo@ref, optionally owner/repo/subdir@ref -- the subdir is handled downstream by
     remotePath.localVcsPath, so the tarball is always the repository root. */
  const parsed = /^([^\/]+)\/([^\/@]+)(?:\/[^@]*)?@(.+)$/.exec( spec );
  if( !parsed )
  throw new Error( `wretry: cannot parse action reference "${ spec }"` );

  const owner = parsed[ 1 ];
  const repo = parsed[ 2 ];
  const ref = parsed[ 3 ];
  const token = process.env.INPUT_GITHUB_TOKEN || '';

  const tmpDir = fs.mkdtempSync( nodePath.join( os.tmpdir(), 'wretry-action-' ) );
  const tarball = nodePath.join( tmpDir, 'action.tgz' );
  const headers = nodePath.join( tmpDir, 'headers.txt' );

  const args =
  [
    '-sS', '-f', '-L',
    '-o', tarball,
    '-D', headers,
    '-H', 'Accept: application/vnd.github+json',
  ];
  if( token )
  args.push( '-H', `Authorization: Bearer ${ token }` );
  else
  console.log( 'wretry: no github_token supplied, fetching anonymously' );
  args.push( `https://api.github.com/repos/${ owner }/${ repo }/tarball/${ ref }` );

  childProcess.execFileSync( 'curl', args, { stdio : [ 'ignore', 'inherit', 'inherit' ] } );

  /* The anonymous REST limit is 60/hr and a token's is far higher, so this line is proof on the
     face of the log that the fetch was authenticated. */
  let limit = 'unknown';
  try
  {
    const match = /x-ratelimit-limit:\s*(\d+)/i.exec( fs.readFileSync( headers, 'utf8' ) );
    if( match )
    limit = match[ 1 ];
  }
  catch( err ) {}
  console.log( `wretry: fetched ${ owner }/${ repo }@${ ref } via authenticated API (x-ratelimit-limit=${ limit })` );

  fs.mkdirSync( localPath, { recursive : true } );
  childProcess.execFileSync
  (
    'tar', [ 'xzf', tarball, '-C', localPath, '--strip-components=1' ],
    { stdio : [ 'ignore', 'inherit', 'inherit' ] }
  );
  fs.rmSync( tmpDir, { recursive : true, force : true } );

  return null;
}

//

function actionConfigRead( actionDir )
{
  let configPath = _.path.join( actionDir, 'action.yml' );
  if( !_.fileProvider.fileExists( configPath ) )
  configPath = _.path.join( actionDir, 'action.yaml' )

  _.assert( _.fileProvider.fileExists( configPath ), 'Expects action path `action.yml` or `action.yaml` in the action dir: ' + actionDir );

  return _.fileProvider.fileRead
  ({
    filePath : configPath,
    encoding : 'yaml',
  });
}

//

function actionOptionsParse( src )
{
  const jsYaml = require( 'js-yaml' );
  return jsYaml.load( src ) || {};
}

//

function optionsExtendByInputDefaults( options, inputs )
{
  const result = Object.create( null );

  for( let key in options )
  result[ key ] = options[ key ];

  if( inputs )
  {
    for( let key in inputs )
    {
      if( key in options )
      {
        if( inputs[ key ].required )
        _.sure( options[ key ] !== undefined, `Please, provide value for option "${ key }"` )
      }
      else
      {
        const defaultValue = inputs[ key ].default;
        if( inputs[ key ].required )
        _.sure( defaultValue !== undefined, `Please, provide value for option "${ key }"` )

        let value = defaultValue;
        if( _.str.is( value ) )
        if( value.startsWith( '${{' ) && value.endsWith( '}}' ) )
        {
          value = evaluateExpression( value );
        }
        result[ key ] = value;
      }
    }
  }

  return result;
}

//

function envOptionsFrom( options )
{
  const result = Object.create( null );
  for( let key in options )
  result[ `INPUT_${ key.replace( / /g, '_' ).toUpperCase() }` ] = options[ key ];
  return result;
}

//

function contextGet( contextName )
{
  if( contextName === 'env' )
  {
    let envContext = JSON.parse( core.getInput( 'env_context' ) );
    if( _.map.keys( envContext ).length === 0 )
    return process.env;
    return envContext;
  }
  else if( contextName === 'github' )
  {
    let githubContext = JSON.parse( core.getInput( 'github_context' ) );
    githubContext = githubContextUpdate( githubContext );
    return githubContext;
  }
  else if( contextName === 'steps' )
  {
    const context = JSON.parse( core.getInput( `${ contextName }_context` ) );
    if( _.fileProvider.fileExists( process.env.GITHUB_OUTPUT ) )
    {
      const rawFile = _.fileProvider.fileRead({ filePath : process.env.GITHUB_OUTPUT });
      const regex = /^(.*)<<ghadelimiter_(.*)(\s*)(.+)\3ghadelimiter_\2/mg;
      const filteredFile = rawFile.replaceAll( regex, '$1=$4' );
      const Ini = require( 'ini' );
      const parsed = Ini.parse( filteredFile );
      context._this = { outputs : parsed, outcome : 'failure', conclusion : 'failure' };
    }
    return context;
  }
  else if(  [ 'job', 'matrix', 'inputs' ].includes( contextName ) )
  {
    const context = JSON.parse( core.getInput( `${ contextName }_context` ) );
    return context;
  }

  _.sure
  (
    false,
    `The requested context "${ contextName }" does not supported by action.`
    + '\nPlease, open an issue with the request for the feature.'
  );

  /* */

  function githubContextUpdate( githubContext )
  {
    if( process.env.RETRY_ACTION )
    {
      const remoteActionPath = remotePathForm( process.env.RETRY_ACTION );
      const localActionPath = _.path.nativize( _.path.join( __dirname, '../../../', remoteActionPath.repo ) );
      githubContext.action_path = localActionPath;
      githubContext.action_ref = remoteActionPath.tag;
    }
    return githubContext;
  }
}

//

function envOptionsSetup( options )
{
  for( let key in options )
  core.exportVariable( key, options[ key ] );
}

//

function shouldExit( config, scriptType )
{
  const using = config.runs.using;
  if( _.strBegins( using, 'node' ) || using === 'docker' )
  {
    if( using === 'docker' && scriptType === 'main' )
    return false;

    const localScriptType = using === 'docker' ? `${ scriptType }-entrypoint` : scriptType;
    if( !config.runs[ localScriptType ] )
    return true;

    if( config.runs[ `${ scriptType }-if` ] )
    {
      return !evaluateExpression( config.runs[ `${ scriptType }-if` ] );
    }
  }

  return false;
}

//

function evaluateExpression( expression, getter )
{
  return GithubActionsParser.evaluateExpression( expression, { get : getter || contextGet } );
}

// --
// export
// --

const Self =
{
  commandsForm,
  remotePathForm,
  actionClone,
  actionConfigRead,
  actionOptionsParse,
  optionsExtendByInputDefaults,
  envOptionsFrom,
  contextGet,
  envOptionsSetup,
  shouldExit,
  evaluateExpression,
};

module.exports = Self;

