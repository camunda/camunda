#!/bin/bash
die () {
    echo >&2 "$@"
    exit 1
}

[[ "$#" -eq 1 ]] || die "1 argument required [NAMESPACE], $# provided"

NAMESPACE=$1

sed -e "s/\${NAMESPACE}/$NAMESPACE/g" -e 's/replicas: 1/replicas: 0/g' < .github/podSpecs/performanceTests/optimize.yml | kubectl apply -f -