#!/bin/sh -eux

# enable external link checker
# export MDBOOK_OUTPUT__LINKCHECK__FOLLOW_WEB_LINKS='true'

cd docs/
mdbook build
