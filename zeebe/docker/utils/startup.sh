#!/bin/sh -xeu

if [ "$ZEEBE_STANDALONE_GATEWAY" = "true" ]; then
    exec /usr/local/zeebe/bin/gateway
elif [ "$ZEEBE_RESTORE" = "true" ]; then
  # Build the restore arguments in the shell's positional parameters
  set --
  if [ "${ZEEBE_RESTORE_ALL_TENANTS:-}" = "true" ]; then
    set -- "$@" --allTenants
  fi
  if [ "${ZEEBE_RESTORE_TENANT_ID:-}" ]; then
    set -- "$@" --tenantId="${ZEEBE_RESTORE_TENANT_ID}"
  fi
  if [ "${ZEEBE_RESTORE_FROM_BACKUP_ID:-}" ]; then
    set -- "$@" --backupId="${ZEEBE_RESTORE_FROM_BACKUP_ID}"
  elif [ "${ZEEBE_RESTORE_FROM_TIMESTAMP:-}" ] && [ "${ZEEBE_RESTORE_TO_TIMESTAMP:-}" ]; then
    set -- "$@" --from="${ZEEBE_RESTORE_FROM_TIMESTAMP}" --to="${ZEEBE_RESTORE_TO_TIMESTAMP}"
  elif [ "${ZEEBE_RESTORE_FROM_TIMESTAMP:-}" ]; then
    set -- "$@" --from="${ZEEBE_RESTORE_FROM_TIMESTAMP}"
  elif [ "${ZEEBE_RESTORE_TO_TIMESTAMP:-}" ]; then
    set -- "$@" --to="${ZEEBE_RESTORE_TO_TIMESTAMP}"
  fi
  exec /usr/local/zeebe/bin/restore "$@"
else
  exec /usr/local/zeebe/bin/broker
fi
