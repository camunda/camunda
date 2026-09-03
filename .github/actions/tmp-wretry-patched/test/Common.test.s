( function _Common_test_s_()
{

'use strict';

if( typeof module !== 'undefined' )
{
  const _ = require( 'wTools' );
  _.include( 'wTesting' );
  _.include( 'wGitTools' );
}

const _ = _global_.wTools;
const __ = _globals_.testing.wTools;
const common = require( '../src/Common.js' );
const core = require( '@actions/core' );

// --
// test
// --

function commandsForm( test )
{
  let commandsForm = common.commandsForm;
  if( process.platform === 'win32' )
  commandsForm = ( arg ) => {
    let res = common.commandsForm( arg );
    test.identical( res[ 0 ], '$ErrorActionPreference = \'stop\'' );
    test.identical( res[ res.length - 1 ], 'if ((Test-Path -LiteralPath variable:\LASTEXITCODE)) { exit $LASTEXITCODE }' );
    res.pop();
    res.shift();
    return res;
  };

  test.case = 'one line command';
  var got = commandsForm( [ 'echo foo' ] );
  test.identical( got, [ 'echo foo' ] );

  test.case = 'multiple commands without backslash';
  var got = commandsForm( [ 'echo foo', 'echo bar' ] );
  test.identical( got, [ 'echo foo', 'echo bar' ] );

  test.case = 'multiline command with bar';
  var got = commandsForm( [ '|', 'echo foo' ] );
  test.identical( got, [ 'echo foo' ] );

  test.case = 'multiline command with bar, multiple commands without backslash';
  var got = commandsForm( [ '|', 'echo foo', 'echo bar' ] );
  test.identical( got, [ 'echo foo', 'echo bar' ] );

  test.case = 'multiline command with backslash';
  var got = commandsForm( [ 'echo \\', 'foo' ] );
  test.identical( got, [ 'echo \\', 'foo' ] );

  test.case = 'multiline command with bar and backslash';
  var got = commandsForm( [ '|', 'echo \\', 'foo' ] );
  test.identical( got, [ 'echo \\', 'foo' ] );

  test.case = 'multiline command with bar and backslash, several commands';
  var got = commandsForm( [ '|', 'echo \\', 'foo', 'echo bar' ] );
  test.identical( got, [ 'echo \\', 'foo', 'echo bar' ] );

  test.case = 'multiline commands with bar and backslash, several commands';
  var got = commandsForm( [ '|', 'echo \\', 'foo', 'echo \\', 'bar' ] );
  test.identical( got, [ 'echo \\', 'foo', 'echo \\', 'bar' ] );

  /* - */

  if( !Config.debug )
  return;

  test.case = 'empty commands';
  test.shouldThrowErrorSync( () => common.commandsForm( [] ) );

  test.case = 'commands starts with bar but has no command';
  test.shouldThrowErrorSync( () => common.commandsForm( [ '|' ] ) );

  test.case = 'command ends with continuation';
  test.shouldThrowErrorSync( () => common.commandsForm( [ 'echo \\' ] ) );
}

//

function remotePathForm( test )
{
  test.open( 'without token' );

  test.case = 'without hash or tag';
  var got = common.remotePathForm( 'action/name' );
  var exp =
  {
    protocol : 'https',
    longPath : 'github.com/action/name',
    tag : 'master',
    localVcsPath : './',
    protocols : [ 'https' ],
    isFixated : false,
    service : 'github.com',
    user : 'action',
    repo : 'name'
  };
  test.identical( got, exp );

  test.case = 'with tag';
  var got = common.remotePathForm( 'action/name@v0.0.0' );
  var exp =
  {
    protocol : 'https',
    longPath : 'github.com/action/name',
    tag : 'v0.0.0',
    localVcsPath : './',
    protocols : [ 'https' ],
    isFixated : false,
    service : 'github.com',
    user : 'action',
    repo : 'name'
  };
  test.identical( got, exp );

  test.case = 'with short hash';
  var got = common.remotePathForm( 'action/name@9b5d00b' );
  var exp =
  {
    protocol : 'https',
    longPath : 'github.com/action/name',
    tag : '9b5d00b',
    localVcsPath : './',
    protocols : [ 'https' ],
    isFixated : false,
    service : 'github.com',
    user : 'action',
    repo : 'name'
  };
  test.identical( got, exp );

  test.case = 'with long hash';
  var got = common.remotePathForm( 'action/name@9b5d00b7245dae0586efca5052f41ae023cb7659' );
  var exp =
  {
    protocol : 'https',
    longPath : 'github.com/action/name',
    tag : '9b5d00b7245dae0586efca5052f41ae023cb7659',
    localVcsPath : './',
    protocols : [ 'https' ],
    isFixated : false,
    service : 'github.com',
    user : 'action',
    repo : 'name'
  };
  test.identical( got, exp );

  /* */

  test.case = 'without hash or tag, subdirectory';
  var got = common.remotePathForm( 'action/name/subdir' );
  var exp =
  {
    protocol : 'https',
    longPath : 'github.com/action/name.git/',
    tag : 'master',
    localVcsPath : 'subdir',
    protocols : [ 'https' ],
    isFixated : false,
    service : 'github.com',
    user : 'action',
    repo : 'name'
  };
  test.identical( got, exp );

  test.case = 'with tag, subdirectory';
  var got = common.remotePathForm( 'action/name/subdir@v0.0.0' );
  var exp =
  {
    protocol : 'https',
    longPath : 'github.com/action/name.git/',
    tag : 'v0.0.0',
    localVcsPath : 'subdir',
    protocols : [ 'https' ],
    isFixated : false,
    service : 'github.com',
    user : 'action',
    repo : 'name'
  };
  test.identical( got, exp );

  test.case = 'with short hash, subdirectory';
  var got = common.remotePathForm( 'action/name/subdir@9b5d00b' );
  var exp =
  {
    protocol : 'https',
    longPath : 'github.com/action/name.git/',
    tag : '9b5d00b',
    localVcsPath : 'subdir',
    protocols : [ 'https' ],
    isFixated : false,
    service : 'github.com',
    user : 'action',
    repo : 'name'
  };
  test.identical( got, exp );

  test.case = 'with long hash';
  var got = common.remotePathForm( 'action/name/subdir@9b5d00b7245dae0586efca5052f41ae023cb7659' );
  var exp =
  {
    protocol : 'https',
    longPath : 'github.com/action/name.git/',
    tag : '9b5d00b7245dae0586efca5052f41ae023cb7659',
    localVcsPath : 'subdir',
    protocols : [ 'https' ],
    isFixated : false,
    service : 'github.com',
    user : 'action',
    repo : 'name'
  };
  test.identical( got, exp );

  test.case = 'with org/.github repo under subdirectory path';
  var got = common.remotePathForm( 'org/.github/actions/foo/bar/action@v1.2.3' );
  var exp =
  {
    protocol : 'https',
    longPath : 'github.com/org/.github.git/',
    tag : 'v1.2.3',
    localVcsPath : 'actions/foo/bar/action',
    protocols : [ 'https' ],
    isFixated : false,
    service : 'github.com',
    user : 'org',
    repo : '.github'
  };
  test.identical( got, exp );

  test.close( 'without token' );

  /* - */

  test.case = 'with short hash, subdirectory and token';
  var got = common.remotePathForm( 'action/name/subdir@9b5d00b', 'token' );
  var exp =
  {
    protocol : 'https',
    longPath : 'oauth2:token@github.com/action/name.git/',
    tag : '9b5d00b',
    localVcsPath : 'subdir',
    protocols : [ 'https' ],
    isFixated : false,
    service : 'github.com',
    user : 'action',
    repo : 'name'
  };
  test.identical( got, exp );
}

//

function actionClone( test )
{
  const a = test.assetFor( false );
  const localPath = a.abs( '.' );

  /* - */

  a.ready.then( () =>
  {
    test.case = 'action without hash or tag, default branch is main';
    var remotePath = common.remotePathForm( 'actions/hello-world-javascript-action' );
    return common.actionClone( localPath, remotePath );
  });
  a.ready.then( ( op ) =>
  {
    test.identical( op, true );
    test.identical( __.git.tagLocalRetrive({ localPath }), 'main' );
    a.fileProvider.filesDelete( localPath );
    return null;
  });

  a.ready.then( () =>
  {
    test.case = 'action without hash or tag';
    var remotePath = common.remotePathForm( 'dmvict/test.action' );
    return common.actionClone( localPath, remotePath );
  });
  a.ready.then( ( op ) =>
  {
    test.identical( op, true );
    test.identical( __.git.tagLocalRetrive({ localPath }), 'master' );
    a.fileProvider.filesDelete( localPath );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'action with tag';
    var remotePath = common.remotePathForm( 'dmvict/test.action@v0.0.2' );
    return common.actionClone( localPath, remotePath );
  });
  a.ready.then( ( op ) =>
  {
    test.identical( op, true );
    test.identical( __.git.tagLocalRetrive({ localPath }), 'v0.0.2' );
    a.fileProvider.filesDelete( localPath );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'action with short hash';
    var remotePath = common.remotePathForm( 'dmvict/test.action@3d21630' );
    return common.actionClone( localPath, remotePath );
  });
  a.ready.then( ( op ) =>
  {
    test.identical( op, true );
    test.identical( __.git.tagLocalRetrive({ localPath }), 'v0.0.1' );
    a.fileProvider.filesDelete( localPath );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'action with long hash';
    var remotePath = common.remotePathForm( 'dmvict/test.action@3d2163092fd3c83e02895189bf8fb845c5dc9e3f' );
    return common.actionClone( localPath, remotePath );
  });
  a.ready.then( ( op ) =>
  {
    test.identical( op, true );
    test.identical( __.git.tagLocalRetrive({ localPath }), 'v0.0.1' );
    a.fileProvider.filesDelete( localPath );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'action with subdirectory';
    var remotePath = common.remotePathForm( 'dmvict/test.action/subaction' );
    return common.actionClone( localPath, remotePath );
  });
  a.ready.then( ( op ) =>
  {
    test.identical( op, true );
    test.identical( __.git.tagLocalRetrive({ localPath }), 'master' );
    a.fileProvider.filesDelete( localPath );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'action with subdirectory and tag';
    var remotePath = common.remotePathForm( 'dmvict/test.action/subaction@v0.0.11' );
    return common.actionClone( localPath, remotePath );
  });
  a.ready.then( ( op ) =>
  {
    test.identical( op, true );
    test.identical( __.git.tagLocalRetrive({ localPath }), 'action_from_subdirectory' );
    a.fileProvider.filesDelete( localPath );
    return null;
  });

  /* - */

  return a.ready;
}

actionClone.timeOut = 60000;

//

function actionConfigRead( test )
{
  const a = test.assetFor( false );
  const actionPath = a.abs( '.' );
  const remoteActionPath = common.remotePathForm( 'dmvict/test.action@v0.0.2' );

  /* - */

  begin().then( () =>
  {
    test.case = 'read directory with action file, extension - yml';
    var got = common.actionConfigRead( actionPath );
    var exp =
    {
      name : 'test.action',
      author : 'dmvict <dm.vict.kr@gmail.com>',
      description : 'An action for testing purpose, no real usage expected.',
      inputs :
      {
        value : { 'description' : 'A test value', 'required' : true, 'default' : 0 }
      },
      runs : { 'using' : 'node12', 'main' : 'src/index.js' }
    };
    test.identical( got, exp );
    a.fileProvider.filesDelete( actionPath );
    return null;
  });

  /* */

  begin().then( () =>
  {
    test.case = 'read directory with action file, extension - yaml';
    a.fileProvider.fileRename({ srcPath : a.abs( 'action.yml' ), dstPath : a.abs( 'action.yaml' ) });
    return null;
  });
  a.ready.then( () =>
  {
    var got = common.actionConfigRead( actionPath );
    var exp =
    {
      name : 'test.action',
      author : 'dmvict <dm.vict.kr@gmail.com>',
      description : 'An action for testing purpose, no real usage expected.',
      inputs :
      {
        value : { 'description' : 'A test value', 'required' : true, 'default' : 0 }
      },
      runs : { 'using' : 'node12', 'main' : 'src/index.js' }
    };
    test.identical( got, exp );
    a.fileProvider.filesDelete( actionPath );
    return null;
  });

  /* - */

  if( Config.debug )
  {
    begin().then( () =>
    {
      test.case = 'config file does not exists';
      a.fileProvider.filesDelete( a.abs( actionPath, 'action.yml' ) );
      test.shouldThrowErrorSync( () => common.actionConfigRead( actionPath ) );
      return null;
    });

    begin().then( () =>
    {
      test.case = 'not known extension';
      a.fileProvider.fileRename({ srcPath : a.abs( 'action.yml' ), dstPath : a.abs( 'action.json' ) });
      test.shouldThrowErrorSync( () => common.actionConfigRead( actionPath ) );
      return null;
    });
  }

  /* - */

  return a.ready;

  /* */

  function begin()
  {
    return a.ready.then( () =>
    {
      a.fileProvider.filesDelete( actionPath );
      return common.actionClone( actionPath, common.remotePathForm( 'dmvict/test.action@v0.0.2' ) );
    });
  }
}

actionConfigRead.timeOut = 20000;

//

function actionOptionsParse( test )
{
  test.case = 'empty array';
  var src = '';
  var got = common.actionOptionsParse( src );
  test.identical( got, {} );

  /* - */

  test.open( 'without spaces' );

  test.case = 'string without delimeter';
  var src = 'str';
  var got = common.actionOptionsParse( src );
  test.identical( got, 'str' );

  test.case = 'string with delimeter, no value';
  var src = 'str:';
  var got = common.actionOptionsParse( src );
  test.identical( got, { str : null } );

  test.case = 'string with delimeter, no key';
  var src = ':str';
  var got = common.actionOptionsParse( src );
  test.identical( got, ':str' );

  test.case = 'string with delimeter, key-value';
  var src = 'str:value';
  var got = common.actionOptionsParse( src );
  test.identical( got, 'str:value' );

  test.case = 'string with delimeter, key-value, value is number';
  var src = 'str:3';
  var got = common.actionOptionsParse( src );
  test.identical( got, 'str:3' );

  test.case = 'several strings';
  var src = 'str:value\nnumber:2';
  var got = common.actionOptionsParse( src );
  test.identical( got, 'str:value number:2' );

  test.case = 'string with uri';
  var src = 'url:https://google.com';
  var got = common.actionOptionsParse( src );
  test.identical( got, 'url:https://google.com' );

  test.close( 'without spaces' );

  /* - */

  test.open( 'with spaces' );

  test.case = 'string with delimeter, no value';
  var src = ' str : ';
  var got = common.actionOptionsParse( src );
  test.identical( got, { str : null } );

  test.case = 'string with delimeter, key-value';
  var src = '  str    : value   ';
  var got = common.actionOptionsParse( src );
  test.identical( got, { str : 'value' } );

  test.case = 'string with delimeter, key-value, value is number';
  var src = '  str : 3';
  var got = common.actionOptionsParse( src );
  test.identical( got, { str : 3 } );

  test.case = 'several strings';
  var src = ' str  : value \n number : 2   ';
  var got = common.actionOptionsParse( src );
  test.identical( got, { str : 'value', number : 2 } );

  test.case = 'string with uri';
  var src = ' url  : https://google.com    ';
  var got = common.actionOptionsParse( src );
  test.identical( got, { url : 'https://google.com' } );

  test.close( 'with spaces' );

  /* - */

  test.open( 'multiline' );

  test.case = 'value is multiline string';
  var src = 'str: |\n  abc\n  def';
  var got = common.actionOptionsParse( src );
  test.identical( got, { str : 'abc\ndef\n' } );

  test.case = 'several pairs, one pair has value with multiline string';
  var src = 'str: |\n  abc\n  def\nnum: 2';
  var got = common.actionOptionsParse( src );
  test.identical( got, { str : 'abc\ndef\n', num : 2 } );

  test.case = 'the last key with bare';
  var src = 'str: |';
  var got = common.actionOptionsParse( src );
  test.identical( got, { str : '' } );

  test.case = 'not last key with bare';
  var src = 'str: |\nnum : 2';
  var got = common.actionOptionsParse( src );
  test.identical( got, { str : '', num : 2 } );

  test.case = 'value is multiline string with different levels';
  var src = 'str: |\n  abc\n    def\n      gih';
  var got = common.actionOptionsParse( src );
  test.identical( got, { str : 'abc\n  def\n    gih\n' } );

  test.case = 'value is multiline string with different levels and empty lines';
  var src = '  str: |\n\n    abc\n      def\n\n        gih\n ';
  var got = common.actionOptionsParse( src );
  test.identical( got, { str : '\nabc\n  def\n\n    gih\n' } );

  test.close( 'multiline' );

  /* */

  test.case = 'value contains dashes';
  var src = 'str-with-dashes: value';
  var got = common.actionOptionsParse( src );
  test.identical( got, { 'str-with-dashes' : 'value' } );

  /* - */

  if( !Config.debug )
  return;

  test.case = 'without key';
  var src = ' :  str  ';
  test.shouldThrowErrorSync( () => common.actionOptionsParse( src ) );
}

//

function optionsExtendByInputDefaults( test )
{
  test.open( 'empty inputs' );

  var inputs = {};

  test.case = 'no options';
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( got, {} );
  test.true( got !== src );

  test.case = 'option with spaces';
  var src = { 'A b c' : '1' };
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( got, { 'A b c' : '1' } );
  test.true( got !== src );

  test.close( 'empty inputs' );

  /* - */

  test.open( 'non empty inputs' );

  var inputs =
  {
    'str' : { description : 'string', default : 'str' },
    'number' : { description : 'number', default : 1 },
    'empty' : { description : 'string', default : '' },
    'null' : { description : 'undefined', default : null },
    'not-defined' : { description : 'undefined', default : undefined },
    'no-default' : { description : 'undefined' },
    'false' : { description : 'undefined', default : false },
  };

  test.case = 'no options';
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  var exp =
  {
    'str' : 'str',
    'number' : 1,
    'empty' : '',
    'null' : null,
    'not-defined' : undefined,
    'no-default' : undefined,
    'false' : false,
  };
  test.identical( got, exp );
  test.true( got !== src );

  test.case = 'option with spaces';
  var src = { 'A b c' : '1' };
  var got = common.optionsExtendByInputDefaults( src, inputs );
  var exp =
  {
    'A b c' : '1',
    'str' : 'str',
    'number' : 1,
    'empty' : '',
    'null' : null,
    'not-defined' : undefined,
    'no-default' : undefined,
    'false' : false,
  };
  test.identical( got, exp );
  test.true( got !== src );

  test.case = 'rewrite options';
  var src =
  {
    'str' : 'foo',
    'number' : 2,
    'empty' : 'empty',
    'null' : 'null',
    'not-defined' : true,
    'no-default' : true,
    'false' : true
  };
  var got = common.optionsExtendByInputDefaults( src, inputs );
  var exp =
  {
    'str' : 'foo',
    'number' : 2,
    'empty' : 'empty',
    'null' : 'null',
    'not-defined' : true,
    'no-default' : true,
    'false' : true,
  };
  test.identical( got, exp );
  test.true( got !== src );

  test.close( 'non empty inputs' );

  /* */

  var inputs = { 'foo' : { description : 'string', required : true, default : undefined } };

  test.case = 'rewrite options with null';
  var src = { 'foo' : null };
  var got = common.optionsExtendByInputDefaults( src, inputs );
  var exp = { 'foo' : null };
  test.identical( got, exp );
  test.true( got !== src );

  test.case = 'rewrite options with empty';
  var src = { 'foo' : '' };
  var got = common.optionsExtendByInputDefaults( src, inputs );
  var exp = { 'foo' : '' };
  test.identical( got, exp );
  test.true( got !== src );

  test.case = 'rewrite options with false';
  var src = { 'foo' : false };
  var got = common.optionsExtendByInputDefaults( src, inputs );
  var exp = { 'foo' : false };
  test.identical( got, exp );
  test.true( got !== src );

  /* - */

  if( !Config.debug )
  return;

  var inputs = { 'foo' : { description : 'string', required : true, default : undefined } };

  test.case = 'required option, no options, default is undefined';
  test.shouldThrowErrorSync( () => common.optionsExtendByInputDefaults( {}, inputs ) );

  test.case = 'required option, option is undefined';
  test.shouldThrowErrorSync( () => common.optionsExtendByInputDefaults( { foo : undefined }, inputs ) );
}

//

function optionsExtendByInputDefaultsEnvAndGithubContextExpressionInputs( test )
{
  const a = test.assetFor( false );
  process.env.GITHUB_EVENT_PATH = a.path.nativize( a.abs( __dirname, '_asset/context/event.json' ) );
  process.env.TEST = 'test';
  process.env.RETRY_ACTION = 'dmvict/test.action@v0.0.2';
  process.env.INPUT_ENV_CONTEXT = '{}';
  process.env.INPUT_GITHUB_CONTEXT =
`{
  "token": "private_token",
  "job": "fast",
  "ref": "refs/heads/master",
  "sha": "df6a916d",
  "repository": "user/repo",
  "repository_owner": "user",
  "repository_owner_id": "47529590",
  "repositoryUrl": "git://github.com/user/repo.git",
  "run_id": "2567345311",
  "run_number": "114",
  "retention_days": "90",
  "run_attempt": "1",
  "artifact_cache_size_limit": "10",
  "repository_id": "438224811",
  "actor_id": "47529590",
  "actor": "user",
  "workflow": "push",
  "head_ref": "",
  "base_ref": "",
  "event_name": "push",
  "event": {
    "after": "df6a916d93",
    "base_ref": null,
    "before": "9e72f69b4382",
    "commits": [
      {
        "author": {
        },
        "committer": {
        },
        "distinct": true,
        "id": "df6a916d93",
        "message": "test",
        "timestamp": "2022-06-27T09:54:49+03:00",
        "tree_id": "a1579bd0a5ae",
        "url": "https://github.com/user/repo/commit/df6a916d93f"
      }
    ],
    "head_commit": {
      "author": {
      },
      "committer": {
      },
      "distinct": true,
      "id": "df6a916d93",
      "message": "test",
      "timestamp": "2022-06-27T09:54:49+03:00",
      "tree_id": "a1579bd0a5ae",
      "url": "https://github.com/user/repo/commit/df6a916d93f"
    },
    "pusher": {
    },
    "ref": "refs/heads/exp2",
    "repository": {
      "default_branch": "master",
      "master_branch": "master"
    },
    "sender": {
    }
  },
  "server_url": "https://github.com",
  "api_url": "https://api.github.com",
  "graphql_url": "https://api.github.com/graphql",
  "ref_name": "master",
  "ref_protected": false,
  "ref_type": "branch",
  "secret_source": "Actions",
  "workspace": "/home/runner/work/repo/repo",
  "action": "__user_test_action",
  "event_path": "/home/runner/work/_temp/_github_workflow/event.json",
  "action_repository": "",
  "action_ref": "",
  "path": "/home/runner/work/_temp/_runner_file_commands/add_path_e2aba804-be04-4f71-963f-a8c64be62d19",
  "env": "/home/runner/work/_temp/_runner_file_commands/set_env_e2aba804-be04-4f71-963f-a8c64be62d19",
  "step_summary": "/home/runner/work/_temp/_runner_file_commands/step_summary_e2aba804-be04-4f71-963f-a8c64be62d19"
}`;

  /* - */

  test.case = 'resolve environment';
  var inputs =
  {
    environment :
    {
      description : 'environment',
      default : '${{ env.TEST }}'
    }
  };
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( got, { environment : 'test' } );
  test.true( got !== src );

  test.case = 'resolve string from github context';
  var inputs =
  {
    github_string :
    {
      description : 'string in github context',
      default : '${{ github.action_path }}'
    }
  };
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( got, { github_string : a.path.nativize( a.abs( __dirname, '../../../test.action' ) ) } );
  test.true( got !== src );

  test.case = 'resolve string from object in github context';
  var inputs =
  {
    github_object :
    {
      description : 'object in github context',
      default : '${{ github.event.repository.default_branch }}'
    },
  };
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( got, { github_object : 'master' } );
  test.true( got !== src );

  test.case = 'resolve expression with environment and defined value';
  var inputs =
  {
    expression_with_strings :
    {
      description : 'compare resolved with value',
      default : '${{ env.TEST == \'test\' }}'
    },
  };
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( got, { expression_with_strings : true } );
  test.true( got !== src );

  test.case = 'resolve expression with two resolved values';
  var inputs =
  {
    expression_with_resolved_strings :
    {
      description : 'compare two resolved values',
      default : '${{ env.TEST == github.ref_name }}'
    },
  };
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( got, { expression_with_resolved_strings : false } );
  test.true( got !== src );

  test.case = 'resolve expression with two resolved values from objects';
  var inputs =
  {
    expression_with_objects_false :
    {
      description : 'compare two resolved values',
      default : '${{ github.event.repository.master_branch != github.event.repository.default_branch }}'
    },
    expression_with_objects_true :
    {
      description : 'compare two resolved values',
      default : '${{ github.event.repository.master_branch == github.event.repository.default_branch }}'
    },
  };
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( got, { expression_with_objects_false : false, expression_with_objects_true : true } );
  test.true( got !== src );

  test.case = 'resolve expression with two resolved values and boolean AND';
  var inputs =
  {
    expression_with_objects_false :
    {
      description : 'compare two resolved values',
      default : '${{ github.event.repository.master_branch != github.event.repository.default_branch && false }}'
    },
    expression_with_objects_true :
    {
      description : 'compare two resolved values',
      default : '${{ github.event.repository.master_branch == github.event.repository.default_branch && true }}'
    },
  };
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( got, { expression_with_objects_false : false, expression_with_objects_true : true } );
  test.true( got !== src );

  test.case = 'resolve expression with two resolved values and boolean OR';
  var inputs =
  {
    expression_with_objects_false :
    {
      description : 'compare two resolved values',
      default : '${{ github.event.repository.master_branch != github.event.repository.default_branch || true }}'
    },
    expression_with_objects_true :
    {
      description : 'compare two resolved values',
      default : '${{ github.event.repository.master_branch == github.event.repository.default_branch || true }}'
    },
  };
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( got, { expression_with_objects_false : true, expression_with_objects_true : true } );
  test.true( got !== src );
}

//

function optionsExtendByInputDefaultsJobContextExpressionInputs( test )
{
  process.env.INPUT_JOB_CONTEXT =
`{
  "status": "success",
  "container": {
    "network": "github_network"
  },
  "services": {
    "postgres": {
      "id": "80ce7090f",
      "ports": {
        "5432": "5432"
      },
      "network": "github_network"
    }
  }
}`;

  /* - */

  test.case = 'resolve full job context';
  var inputs =
  {
    job_status :
    {
      description : 'job.status',
      default : '${{ toJSON( job ) }}'
    }
  };
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( _.map.keys( got ), [ 'job_status' ] );
  var parsed = JSON.parse( got.job_status );
  test.identical( _.map.keys( parsed ), [ 'status', 'container', 'services' ] );
  test.identical( parsed.status, 'success' );
  test.identical( _.map.keys( parsed.container ), [ 'network' ] );
  test.identical( _.map.keys( parsed.services ), [ 'postgres' ] );
  test.identical( _.map.keys( parsed.services.postgres ), [ 'id', 'ports', 'network' ] );
  test.identical( parsed.container.network, parsed.services.postgres.network );
  test.identical( parsed.container.network, 'github_network' );
  test.identical( parsed.services.postgres.id, '80ce7090f' );
  test.identical( parsed.services.postgres.ports, { '5432' : '5432' } );
  test.true( got !== src );

  test.case = 'resolve job status, field always exists';
  var inputs =
  {
    job_status :
    {
      description : 'job.status',
      default : '${{ job.status }}'
    }
  };
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( got, { job_status : 'success' } );
  test.true( got !== src );

  test.case = 'resolve not existed field';
  var inputs =
  {
    job_network :
    {
      description : 'unknown',
      default : '${{ job.network }}'
    }
  };
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( got, { job_network : '' } );
  test.true( got !== src );

  test.case = 'resolve nested field';
  var inputs =
  {
    job_network :
    {
      description : 'network',
      default : '${{ job.container.network }}'
    }
  };
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( got, { job_network : 'github_network' } );
  test.true( got !== src );
}

optionsExtendByInputDefaultsJobContextExpressionInputs.timeOut = 30000;

//

function optionsExtendByInputDefaultsMatrixContextExpressionInputs( test )
{
  process.env.INPUT_MATRIX_CONTEXT =
`{
  "os": "ubuntu-latest",
  "version" : 16
}`;

  /* - */

  test.case = 'resolve full context';
  var inputs =
  {
    matrix :
    {
      description : 'matrix',
      default : '${{ toJSON( matrix ) }}'
    }
  };
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( got, { matrix : '{"os":"ubuntu-latest","version":16}' } );
  test.true( got !== src );

  test.case = 'resolve field';
  var inputs =
  {
    matrix :
    {
      description : 'matrix field',
      default : '${{ matrix.os }}'
    }
  };
  var src = {};
  var got = common.optionsExtendByInputDefaults( src, inputs );
  test.identical( got, { matrix : 'ubuntu-latest' } );
  test.true( got !== src );
}

//

function envOptionsFrom( test )
{
  test.case = 'no options';
  var src = {};
  var got = common.envOptionsFrom( src );
  test.identical( got, {} );
  test.true( got !== src );

  test.case = 'simple option, lower case';
  var src = { 'a' : '1' };
  var got = common.envOptionsFrom( src );
  test.identical( got, { INPUT_A : '1' } );
  test.true( got !== src );

  test.case = 'option with spaces, lower case';
  var src = { 'a b c' : '1' };
  var got = common.envOptionsFrom( src );
  test.identical( got, { INPUT_A_B_C : '1' } );
  test.true( got !== src );

  test.case = 'simple option, upper case';
  var src = { 'A' : '1' };
  var got = common.envOptionsFrom( src );
  test.identical( got, { INPUT_A : '1' } );
  test.true( got !== src );

  test.case = 'option with spaces, upper case';
  var src = { 'A B C' : '1' };
  var got = common.envOptionsFrom( src );
  test.identical( got, { INPUT_A_B_C : '1' } );
  test.true( got !== src );

  test.case = 'option with spaces, mixed case';
  var src = { 'A b c' : '1' };
  var got = common.envOptionsFrom( src );
  test.identical( got, { INPUT_A_B_C : '1' } );
  test.true( got !== src );

  test.case = 'option with spaces and dash, mixed case';
  var src = { 'A-b c' : '1' };
  var got = common.envOptionsFrom( src );
  test.identical( got, { 'INPUT_A-B_C' : '1' } );
  test.true( got !== src );
}

//

function contextGet( test )
{
  const a = test.assetFor( false );

  process.env.INPUT_ENV_CONTEXT = '{}';
  process.env.INPUT_GITHUB_CONTEXT = '{}';;
  process.env.INPUT_JOB_CONTEXT = '{}';;
  process.env.INPUT_MATRIX_CONTEXT = '{}';;
  process.env.RETRY_ACTION = 'user/action-repo@master';

  /* - */

  test.case = 'env context';
  process.env.INPUT_ENV_CONTEXT = `{\n  "OS": "ubuntu-latest"\n}`;
  var got = common.contextGet( 'env' );
  test.identical( got, { OS : 'ubuntu-latest' } );
  var got = common.contextGet( 'github' );
  test.identical( got, { action_path : a.path.nativize( a.abs( __dirname, '../../../action-repo' ) ), action_ref : 'master' } );
  var got = common.contextGet( 'job' );
  test.identical( got, {} );
  var got = common.contextGet( 'matrix' );
  test.identical( got, {} );
  process.env.INPUT_ENV_CONTEXT = '{\n  "default": "default"\n}';

  test.case = 'github context';
  process.env.INPUT_GITHUB_CONTEXT = `{\n  "repository": "user/repo"\n}`;
  var got = common.contextGet( 'env' );
  test.identical( got, { default : 'default' } );
  var got = common.contextGet( 'github' );
  test.identical( got, { repository : 'user/repo', action_path : a.path.nativize( a.abs( __dirname, '../../../action-repo' ) ), action_ref : 'master' } );
  var got = common.contextGet( 'job' );
  test.identical( got, {} );
  var got = common.contextGet( 'matrix' );
  test.identical( got, {} );
  process.env.INPUT_GITHUB_CONTEXT = '{}';

  test.case = 'job context';
  process.env.INPUT_JOB_CONTEXT = `{\n  "status": "success"\n}`;
  var got = common.contextGet( 'env' );
  test.identical( got, { default : 'default' } );
  var got = common.contextGet( 'github' );
  test.identical( got, { action_path : a.path.nativize( a.abs( __dirname, '../../../action-repo' ) ), action_ref : 'master' } );
  var got = common.contextGet( 'job' );
  test.identical( got, { status : 'success' } );
  var got = common.contextGet( 'matrix' );
  test.identical( got, {} );
  process.env.INPUT_JOB_CONTEXT = '{}';

  test.case = 'matrix context';
  process.env.INPUT_MATRIX_CONTEXT = `{\n  "version": "10"\n}`;
  var got = common.contextGet( 'env' );
  test.identical( got, { default : 'default' } );
  var got = common.contextGet( 'github' );
  test.identical( got, { action_path : a.path.nativize( a.abs( __dirname, '../../../action-repo' ) ), action_ref : 'master' } );
  var got = common.contextGet( 'job' );
  test.identical( got, {} );
  var got = common.contextGet( 'matrix' );
  test.identical( got, { version : '10' } );
  process.env.INPUT_MATRIX_CONTEXT = '{}';

  delete process.env.RETRY_ACTION;

  /* - */

  if( !Config.debug )
  return;

  test.case = 'pass no context name';
  test.shouldThrowErrorSync( () => common.contextGet() );

  test.case = 'get unknown context';
  test.shouldThrowErrorSync( () => common.contextGet( 'unknown' ) );

  test.case = 'invalid context name value';
  test.shouldThrowErrorSync( () => common.contextGet( null ) );
}

//

function envOptionsSetup( test )
{
  const beginEnvs = _.map.extend( null, process.env );

  /* */

  test.case = 'empty options';
  var src = {};
  common.envOptionsSetup( src );
  test.identical( _.mapBut_( null, process.env, beginEnvs ), {} );

  test.case = 'several options';
  var src = { 'option1' : 'a', 'INPUT_V' : 'input' };
  common.envOptionsSetup( src );
  test.identical( _.mapBut_( null, process.env, beginEnvs ), { 'option1' : 'a', 'INPUT_V' : 'input' } );
  delete process.env.option1;
  delete process.env.INPUT_V;

  test.case = 'option with multiline value';
  var src = { 'INPUT_V' : 'abc\ndef' };
  common.envOptionsSetup( src );
  test.identical( _.mapBut_( null, process.env, beginEnvs ), { 'INPUT_V' : 'abc\ndef' } );
  delete process.env.option1;
  delete process.env.INPUT_V;
}

//

function shouldExit( test )
{
  test.open( 'without conditions' );

  var config =
  {
    runs:
    {
      using : 'node20',
      main : 'index.js'
    }
  };

  test.case = 'only main in config, pre';
  var got = common.shouldExit( config, 'pre' );
  test.identical( got, true );

  test.case = 'only main in config, main';
  var got = common.shouldExit( config, 'main' );
  test.identical( got, false );

  test.case = 'only main in config, post';
  var got = common.shouldExit( config, 'post' );
  test.identical( got, true );

  test.case = 'only main in config, unknown script type';
  var got = common.shouldExit( config, 'unknown' );
  test.identical( got, true );

  /* */

  var config =
  {
    runs:
    {
      using : 'node20',
      pre : 'pre.js',
      main : 'index.js',
      post : 'post.js',
    }
  };

  test.case = 'all entries in config, pre';
  var got = common.shouldExit( config, 'pre' );
  test.identical( got, false );

  test.case = 'all entries in config, main';
  var got = common.shouldExit( config, 'main' );
  test.identical( got, false );

  test.case = 'all entries in config, post';
  var got = common.shouldExit( config, 'post' );
  test.identical( got, false );

  /* */

  var config =
  {
    runs:
    {
      using : 'docker',
      image : 'Dockerfile',
    }
  };

  test.case = 'only image in config, pre';
  var got = common.shouldExit( config, 'pre' );
  test.identical( got, true );

  test.case = 'only main in config, main';
  var got = common.shouldExit( config, 'main' );
  test.identical( got, false );

  test.case = 'only main in config, post';
  var got = common.shouldExit( config, 'post' );
  test.identical( got, true );

  test.case = 'only main in config, unknown script type';
  var got = common.shouldExit( config, 'unknown' );
  test.identical( got, true );

  /* */

  var config =
  {
    runs:
    {
      'using' : 'docker',
      'image' : 'Dockerfile',
      'pre-entrypoint' : 'pre.sh',
      'post-entrypoint' : 'post.sh',
    }
  };

  test.case = 'all entries in config, pre';
  var got = common.shouldExit( config, 'pre' );
  test.identical( got, false );

  test.case = 'all entries in config, main';
  var got = common.shouldExit( config, 'main' );
  test.identical( got, false );

  test.case = 'all entries in config, post';
  var got = common.shouldExit( config, 'post' );
  test.identical( got, false );

  test.close( 'without conditions' );

  /* - */

  test.open( 'with conditions' );

  var config =
  {
    runs:
    {
      'using' : 'node20',
      'pre' : 'pre.js',
      'pre-if' : 'env.TEST_CI == true',
      'main' : 'index.js',
      'post' : 'post.js',
    }
  };

  test.case = 'all entries in config, pre with condition, should be false';
  core.exportVariable( 'INPUT_ENV_CONTEXT', '{"TEST_CI": true}' );
  var got = common.shouldExit( config, 'pre' );
  test.identical( got, false );

  test.case = 'all entries in config, pre with condition, should be true';
  core.exportVariable( 'INPUT_ENV_CONTEXT', '{"TEST_CI": false}' );
  var got = common.shouldExit( config, 'pre' );
  test.identical( got, true );

  /* */

  var config =
  {
    runs:
    {
      'using' : 'docker',
      'image' : 'Dockerfile',
      'pre-if' : 'env.TEST_CI == true',
      'pre-entrypoint' : 'pre.sh',
      'post-entrypoint' : 'post.sh',
    }
  };

  test.case = 'all entries in config, pre with condition, should be false';
  core.exportVariable( 'INPUT_ENV_CONTEXT', '{"TEST_CI": true}' );
  var got = common.shouldExit( config, 'pre' );
  test.identical( got, false );

  test.case = 'all entries in config, pre with condition, should be true';
  core.exportVariable( 'INPUT_ENV_CONTEXT', '{"TEST_CI": false}' );
  var got = common.shouldExit( config, 'pre' );
  test.identical( got, true );

  test.close( 'with conditions' );
}

//

function evaluateExpression( test )
{
  var contextGet = ( _contextName ) => {
    return {
      _this:
      {
        outputs: {
          status: 'FAILED'
        }
      }
    };
  };

  test.case = 'evaluate simple expression to true';
  var got = common.evaluateExpression( 'steps._this.outputs.status == \'FAILED\'', contextGet );
  test.identical( got, true );

  test.case = 'evaluate simple expression to false';
  var got = common.evaluateExpression( 'steps._this.outputs.status != \'FAILED\'', contextGet );
  test.identical( got, false );

  test.case = 'evaluate combined expression to true';
  var got = common.evaluateExpression
  (
    '(steps._this.outputs.status != \'WARN\') && (steps._this.outputs.status == \'FAILED\')', contextGet
  );
  test.identical( got, true );

  test.case = 'evaluate simple expression to false';
  var got = common.evaluateExpression
  (
    '(steps._this.outputs.status != \'WARN\') && (steps._this.outputs.status != \'FAILED\')', contextGet
  );
  test.identical( got, false );
}

// --
// declare
// --

const Proto =
{
  name : 'Common',
  silencing : 1,

  tests :
  {
    commandsForm,
    remotePathForm,
    actionClone,
    actionConfigRead,
    actionOptionsParse,
    optionsExtendByInputDefaults,
    optionsExtendByInputDefaultsEnvAndGithubContextExpressionInputs,
    optionsExtendByInputDefaultsJobContextExpressionInputs,
    optionsExtendByInputDefaultsMatrixContextExpressionInputs,
    envOptionsFrom,
    contextGet,
    envOptionsSetup,
    shouldExit,
    evaluateExpression,
  },
};

//

const Self = wTestSuite( Proto );
if( typeof module !== 'undefined' && !module.parent )
wTester.test( Self.name );

})();

