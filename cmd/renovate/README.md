## Local Renovate Tests

If you want to tweak the [renovate config](../../.github/renovate.json), it's a good idea to test this locally.
You can make use of the script [renovate-local.sh](./renovate-local.sh) to do so.

> [!IMPORTANT]
> Make sure you've installed [GitHub CLI](https://github.com/cli/cli/blob/trunk/docs/install_linux.md) locally.

## Usage in this repo

You can just execute `./cmd/renovate/renovate-local.sh`.

## Usage in other repos

If you want to use this script in other repositories, you can simply execute the following one-liner:

```shell
# make sure your terminal location is at the root of the current repository
curl https://raw.githubusercontent.com/camunda/camunda/refs/heads/main/cmd/renovate/renovate-local.sh | bash
```

## Options

The script itself will automatically login to Github.
It detects the name of the current repository and the renovate config (located in the project's root or under `.github`) within this repository.
This is why you need to be located in the repositories root with your terminal session.
You can however set the following environment variables to override default behavior:
- `LOCAL_RENOVATE_CONFIG` - filename (with full path) of the local renovate config to use
- `REPO_NAME` - Github repository name (e.g. `camunda/camunda`)
