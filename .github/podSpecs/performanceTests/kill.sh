#!/bin/bash
die() {
    echo >&2 "$@"
    exit 1
}

[[ "$#" -eq 1 ]] || die "1 argument required [NAMESPACE], $# provided"

NAMESPACE=$1

sed -e "s/\${NAMESPACE}/$NAMESPACE/g" <.github/podSpecs/performanceTests/ns.yml | kubectl delete -f -
