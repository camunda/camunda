#!/usr/bin/env bash
set -o errexit
set -o errtrace

cwd="$(pwd)"
vault_password_file="${ANSIBLE_VAULT_PASSWORD_FILE:-$cwd/.vault_password}"
if [ ! -e "${vault_password_file}" ] && [ ! -e "${HOME}/${vault_password_file}" ]; then
  echo """
Error:
Vault password file '${vault_password_file}' doesn't exists in current directory and not in ${HOME} directory.
Please store it in current directory as '.vault_password' or set the env variable 'ANSIBLE_VAULT_PASSWORD_FILE' accordingly.
    """
  exit 1;
elif [ -e "${HOME}/${vault_password_file}" ]; then
  export ANSIBLE_VAULT_PASSWORD_FILE="${HOME}/${vault_password_file}"
else
  export ANSIBLE_VAULT_PASSWORD_FILE="${vault_password_file}"
fi
