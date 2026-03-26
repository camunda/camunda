## Local Renovate Tests

If you want to tweak the [renovate config](../../.github/renovate.json), it's a good idea to test this locally.
You can make use of the script [renovate-local.sh](./renovate-local.sh) to do so.

## Prerequisites

> [!IMPORTANT]
> - [GitHub CLI](https://github.com/cli/cli/blob/trunk/docs/install_linux.md) — required
> - [Docker](https://docs.docker.com/get-docker/) — required (runs the Renovate container)
> - [jq](https://jqlang.github.io/jq/download/) — required (JSON processing for secrets)
> - [HashiCorp Vault CLI](https://developer.hashicorp.com/vault/install) — recommended for proper testing with real registry credentials

### Installing Vault CLI

```shell
# macOS
brew tap hashicorp/tap
brew install hashicorp/tap/vault

# Linux (Ubuntu/Debian)
wget -O - https://apt.releases.hashicorp.com/gpg | sudo gpg --dearmor -o /usr/share/keyrings/hashicorp-archive-keyring.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(grep -oP '(?<=UBUNTU_CODENAME=).*' /etc/os-release || lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/hashicorp.list
sudo apt update && sudo apt install vault
```

For other Linux distributions, see the [official Vault install guide](https://developer.hashicorp.com/vault/install).

### Authenticating with Vault

```shell
export VAULT_ADDR="https://vault.int.camunda.com"
vault login -method=oidc
```

This opens a browser window for SSO authentication. The session token is cached locally and reused by the script.

## Usage in this repo

For proper testing with real registry credentials (recommended):

```shell
export VAULT_ADDR="https://vault.int.camunda.com"
vault login -method=oidc
./cmd/renovate/renovate-local.sh
```

Without Vault, the script still runs but uses dummy placeholder values for secrets. This means registry lookups requiring authentication (e.g. `reg.mini.dev`) will fail, which may abort the Renovate run early depending on which managers are enabled.

## Usage in other repos

If you want to use this script in other repositories, you can simply execute the following one-liner:

```shell
# make sure your terminal location is at the root of the current repository
curl https://raw.githubusercontent.com/camunda/camunda/refs/heads/main/cmd/renovate/renovate-local.sh | bash
```

## Secrets

The Renovate config references secrets (e.g. `{{ secrets.INFRA_MINIMUS_REGISTRY_TOKEN }}`).
When Vault is authenticated, the script automatically fetches real values. The mapping from Renovate secret names to Vault fields is defined in `vault_field_for_secret()` inside the script. To add a new secret, add a `case` entry there.

## Options

The script itself will automatically login to Github.
It detects the name of the current repository and the renovate config (located in the project's root or under `.github`) within this repository.
This is why you need to be located in the repositories root with your terminal session.
You can however set the following environment variables to override default behavior:
- `LOCAL_RENOVATE_CONFIG` - filename (with full path) of the local renovate config to use
- `REPO_NAME` - Github repository name (e.g. `camunda/camunda`)
- `RENOVATE_ENABLED_MANAGERS` - JSON array of Renovate managers to enable (e.g. `'["maven","npm"]'`). Useful to skip slow managers like `docker` during testing.
