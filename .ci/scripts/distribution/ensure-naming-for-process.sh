#!/bin/bash -eu

FIND_OUTPUT=$(find . -iname '*workflow*' -not -path '.*/node_modules/*' -not -path '.*/target/*' -not -path '.*/.git/*'  -not -path '.*/.github/*' -not -path '.*/vendor/*')

# if grep doesn't find a match it exits with 1, which we ignore as we check the output later
GREP_OUTPUT=$(grep --exclude-dir={.git,node_modules,target,.ci,.github,vendor} -i -P 'workflow(?![-\s]+(?i)engine)' -r . || true)

if [ -n "${FIND_OUTPUT}" ] || [ -n "${GREP_OUTPUT}" ]; then
	echo "Found occurence of workflow in file names or content"
	echo "## File Names:"
	echo "${FIND_OUTPUT}"
	echo "## Content:"
	echo "${GREP_OUTPUT}"
	exit 1
fi
